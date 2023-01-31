package ru.bitel.bgbilling.scripts.services.custom.fialka;

import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;
import org.apache.log4j.Logger;
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
import ru.bitel.bgbilling.modules.inet.api.common.bean.InetAccountingPeriod;

public class InetTransfer
	extends GlobalScriptBase
{
	private static int СID_FROM = 25710;//cid договора, с которого забираем модуль Inet
	private static int CID_TO = 25710;//cid договора, на который переносим модуль Inet
	
	private static int INET_MID = 15;//id модуля Inet
	private static final Logger logger = Logger.getLogger( InetTransfer.class );
	
	@Override
	public void execute( Setup setup, ConnectionSet connectionSet )
		throws Exception
	{
		logger.info("START INET TRANSFER");
		transferInet(connectionSet);
		logger.info("FINISH INET TRANSFER");
	}

	public void transferInet(ConnectionSet connectionSet) throws Exception {
		Connection connection = connectionSet.getConnection();
		Instant today = Instant.now();
		ContractStatusManager contractStatusManager = new ContractStatusManager(connection);
		int currentStatus = contractStatusManager.getStatus(СID_FROM, java.util.Date.from(today)).getStatus();
		ServerContext serverContext = ServerContext.get();
		InetServService inetServService = serverContext.getService(InetServService.class, INET_MID);
		InetDeviceService inetDeviceService = serverContext.getService(InetDeviceService.class, INET_MID);
		AccountingPeriodService accountingPeriodService = serverContext.getService(AccountingPeriodService.class, INET_MID);
		List<InetServ> listInetServ = inetServService.inetServList(СID_FROM, null);
		ContractManager contractManager = new ContractManager (connection);
		ContractModuleManager contractModuleManager = new ContractModuleManager (connection);
		contractModuleManager.addContractModule(CID_TO, INET_MID);
		InetAccountingPeriod inetAccountingPeriod = new InetAccountingPeriod();
		inetAccountingPeriod.setContractId(CID_TO);
		inetAccountingPeriod.setDateFrom(java.util.Date.from(today));
		java.util.Date dateTo = new java.util.Date();
		dateTo.setYear(199);
		inetAccountingPeriod.setDateTo(dateTo);
		accountingPeriodService.periodUpdate(inetAccountingPeriod);
		for (InetServ inetServ :listInetServ) {
			InetDevice inetDevice = inetDeviceService.inetDeviceGet(inetServ.getDeviceId());
			String host = inetDevice.getHost();
			String interfaceTitle ="";
			String comment = "";
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
					contractManager.deleteContractGroup(СID_FROM, 34);
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
			contractStatus.setContractId(СID_FROM);
			contractStatus.setDateFrom(java.util.Date.from(today));
			contractStatus.setStatus(currentStatus);
			contractStatus.setComment(comment);
			contractStatusManager.changeStatus(contractStatus, 0);
			InetServ inetServTo = inetServ.clone();
			inetServTo.setId(0);
			inetServTo.setContractId(CID_TO);
			inetServTo.setDateFrom(java.util.Date.from(today));
			logger.info(СID_FROM + ": remove " +comment);
			inetServService.inetServDelete(inetServ.getId(), true);
			logger.info(CID_TO + ": add " +comment);
			inetServService.inetServUpdate(inetServTo, new ArrayList<InetServOption>(), false, false, 0);
		}
		contractModuleManager.deleteContractModule(СID_FROM, INET_MID);
		ContractParameterManager contractParameterManager = new ContractParameterManager(connection);
		contractParameterManager.deleteDateParam(СID_FROM, 41, 0);
		contractParameterManager.deleteStringParam(СID_FROM, 43, 0);
		contractManager.deleteContractGroup(СID_FROM, 18);
		contractManager.addContractGroup(СID_FROM, 21);
		contractManager.addContractGroup(CID_TO, 18);
		contractManager.deleteContractGroup(CID_TO, 21);
	}
}