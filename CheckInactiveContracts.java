package ru.bitel.bgbilling.scripts.services.custom.fialka;

import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import org.apache.log4j.Logger;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;
import bitel.billing.server.contract.bean.*;
import java.sql.*;
import java.util.*;
import java.math.*;
import java.time.*;
import ru.bitel.bgbilling.kernel.container.managed.ServerContext;
import ru.bitel.bgbilling.modules.inet.api.common.service.*;
import ru.bitel.bgbilling.modules.inet.api.common.bean.*;
import java.time.temporal.ChronoUnit;
import java.net.InetAddress;
import ru.bitel.bgbilling.kernel.contract.api.server.bean.ContractTariffDao;
import ru.bitel.bgbilling.kernel.contract.api.common.bean.ContractTariff;
import ru.bitel.common.model.Period;
import ru.bitel.bgbilling.kernel.contract.limit.common.service.ContractLimitService;

public class CheckInactiveContracts
	extends GlobalScriptBase
{
	static int INET_MID = 15;
	static String INET_INTERVAL = "4 MONTH";
	static Integer[] CTV_TARIFFS = new Integer[] {76,101,108,109,169,276,282};
	private static final Logger logger = Logger.getLogger( CheckInactiveContracts.class );

	@Override
	public void execute( Setup setup, ConnectionSet connectionSet ) throws Exception {
		logger.info("BEGIN INACTIVE " + INET_INTERVAL);
		String query = String.format("SELECT t_cs.cid FROM contract_status t_cs LEFT JOIN contract_module t_cm ON t_cs.cid=t_cm.cid LEFT JOIN contract t_c ON t_cs.cid=t_c.id WHERE t_cm.mid=15 AND (t_cs.date2 IS NULL OR t_cs.date2 > CURDATE()) AND t_cs.status<>0 AND t_cs.date1 < CURDATE() - INTERVAL %s", INET_INTERVAL);
		Connection connection = connectionSet.getConnection();
		try(PreparedStatement preparedStatement = connection.prepareStatement(query);
		ResultSet resultSet = preparedStatement.executeQuery()) {
			while (resultSet.next()) {
				int cid = resultSet.getInt(1);
				print(cid);
				checkTariff(cid, connection);
				removeInet(cid, connection);
			}
		}
		logger.info("END INACTIVE " + INET_INTERVAL);
	}

	/**
	 * Проверяет тариф в договоре cid, если он совмещенный в МКД, то меняет на КТВ и активирует договор
	 *
	 * @param cid
	 * @param connection
	 * @throws Exception
	 */
	public void checkTariff (int cid, Connection connection) throws Exception {
		Instant today = Instant.now();
		Instant yesterday = today.minus(1, ChronoUnit.DAYS);
		ContractStatusManager contractStatusManager = new ContractStatusManager(connection);
		int currentStatus = contractStatusManager.getStatus(cid, java.util.Date.from(today)).getStatus();
		if (currentStatus < 4) {
			ContractTariffDao contractTariffDao = new ContractTariffDao(connection);
			List<ContractTariff> listContractTariff = contractTariffDao.list(cid, new java.util.Date());
			for (ContractTariff contractTariff :listContractTariff) {
				List<Integer> listCtvTariffs = Arrays.asList(CTV_TARIFFS);
				if (contractTariff.getTariffPlanId() == 92) {
					Period period = contractTariff.getPeriod();
					period.setDateTo(java.util.Date.from(yesterday));
					contractTariff.setPeriod(period);
					contractTariff.setComment(INET_INTERVAL + " INACTIVE");
					logger.info( cid + ": close static ip tariff" );
//					print("    Inactive: close static ip tariff");
					contractTariffDao.update(contractTariff);
				}
				if (listCtvTariffs.contains(contractTariff.getTariffPlanId())) {
					Period period = contractTariff.getPeriod();
					period.setDateTo(java.util.Date.from(yesterday));
					contractTariff.setPeriod(period);
					contractTariff.setComment(INET_INTERVAL + " INACTIVE");
					logger.info( cid + ": close old tariff" );
//					print("    Inactive: close old tariff");
					contractTariffDao.update(contractTariff);
					Period periodCTV = new Period();
					periodCTV.setDateFrom(java.util.Date.from(today));
					ContractTariff contractTariffCTV = new ContractTariff();
					contractTariffCTV.setPeriod(periodCTV);
					contractTariffCTV.setTariffPlanId(275);
					contractTariffCTV.setComment(INET_INTERVAL + " INACTIVE");
					contractTariffCTV.setContractId(cid);
//					print("    Inactive: add ctv tariff");
					logger.info( cid + ": add ctv tariff" );
					contractTariffDao.update(contractTariffCTV);
					ContractStatus contractStatus = new ContractStatus();
					contractStatus.setContractId(cid);
					contractStatus.setDateFrom(java.util.Date.from(today));
					contractStatus.setStatus(0);
					contractStatus.setComment(INET_INTERVAL + " INACTIVE");
//					print("    Inactive: change contract status to active");
					logger.info( cid + ": change contract status to active" );
					contractStatusManager.changeStatus(contractStatus, 0);
					ServerContext serverContext = ServerContext.get();
					ContractLimitService contractLimitService = serverContext.getService(ContractLimitService.class, INET_MID);
//					print("    Inactive: change contract limit to 0");
					logger.info( cid + ": change contract limit to 0" );
					contractLimitService.updateContractLimit(cid, new BigDecimal(0), INET_INTERVAL + " INACTIVE") ;
				}
			}
		}
	}

	/**
	 * Удалит модуль Inet из договора cid, указав параметры из модуля в статус
	 *
	 * @param cid
	 * @param connection
	 * @throws Exception
	 */
	public void removeInet(int cid, Connection connection) throws Exception {
		Instant today = Instant.now();
		ContractStatusManager contractStatusManager = new ContractStatusManager(connection);
		int currentStatus = contractStatusManager.getStatus(cid, java.util.Date.from(today)).getStatus();
		ServerContext serverContext = ServerContext.get();
		InetServService inetServService = serverContext.getService(InetServService.class, INET_MID);
		InetDeviceService inetDeviceService = serverContext.getService(InetDeviceService.class, INET_MID);
		List<InetServ> listInetServ = inetServService.inetServList(cid, null);
		ContractManager contractManager = new ContractManager (connection);
		String comment = "";
		for (InetServ inetServ :listInetServ) {
			InetDevice inetDevice = inetDeviceService.inetDeviceGet(inetServ.getDeviceId());
			String host = inetDevice.getHost();
			String interfaceTitle ="";
			int vlan = 0;
			switch (inetServ.getTypeTitle()) {
				case "White-IP":
					interfaceTitle = InetAddress.getByAddress(inetServ.getAddressFrom()).getHostAddress();
					comment = String.format("Inet: %s", interfaceTitle);
//					currentStatus = 4;
					break;
				case "Gray-IP":
					interfaceTitle = inetServ.getInterfaceTitle();
					vlan = inetServ.getVlan();
					comment = String.format("Inet: %s | %s | %d", host, interfaceTitle, vlan);
					contractManager.deleteContractGroup(cid, 34);
					if (currentStatus == 3){
						currentStatus = 4;
					}
					break;
				case "GePON":
					interfaceTitle = inetServ.getInterfaceTitle();
					vlan = inetServ.getVlan();
					String mac = inetServ.macAddressToString(inetServ.getMacAddressListBytes());
					comment = String.format("Inet: %s | %s | %d | %s", host, interfaceTitle, vlan, mac);
					currentStatus = 4;
					break;
			}
			ContractStatus contractStatus = new ContractStatus();
			contractStatus.setContractId(cid);
			contractStatus.setDateFrom(java.util.Date.from(today));
			contractStatus.setStatus(currentStatus);
			contractStatus.setComment(comment);
			contractStatusManager.changeStatus(contractStatus, 0);
			inetServService.inetServDelete(inetServ.getId(), true);
//			print("    " + comment);
			logger.info(cid + ": remove " +comment);
		}
		ContractModuleManager contractModuleManager = new ContractModuleManager (connection);
		contractModuleManager.deleteContractModule(cid, INET_MID);
		ContractParameterManager contractParameterManager = new ContractParameterManager(connection);
		String stringParam56 = Optional.ofNullable(contractParameterManager.getStringParam(cid, 56)).orElse("");
		contractParameterManager.updateStringParam(cid, 56, stringParam56 + comment + ';', 0);
		contractParameterManager.deleteDateParam(cid, 41, 0);
		contractParameterManager.deleteStringParam(cid, 43, 0);
		contractManager.deleteContractGroup(cid, 18);
		contractManager.addContractGroup(cid, 21);
	}
}