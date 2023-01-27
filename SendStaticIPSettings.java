package ru.bitel.bgbilling.scripts.services.custom.fialka;

import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;
import bitel.billing.server.util.MailMsg;
import bitel.billing.server.contract.bean.ContractParameterManager;
import org.apache.log4j.Logger;
import java.sql.Connection;
import bitel.billing.server.contract.bean.ContractUtils;
import ru.bitel.bgbilling.kernel.container.managed.ServerContext;
import ru.bitel.bgbilling.modules.inet.api.common.service.InetServService;
import ru.bitel.bgbilling.modules.inet.api.common.service.InetDeviceService;
import ru.bitel.bgbilling.modules.inet.api.common.bean.InetServ;
import ru.bitel.oss.systems.inventory.resource.common.ResourceService;
import ru.bitel.oss.systems.inventory.resource.common.bean.IpResource;
import java.net.InetAddress;
import java.util.*;

public class SendStaticIPSettings
	extends GlobalScriptBase
{
	private int CONTRACT_ID = 25710;
	private int EMAIL_PID = 13;
	private int INET_MID = 15;
	private static final Logger logger = Logger.getLogger(SendStaticIPSettings.class);
	
	@Override
	public void execute( Setup setup, ConnectionSet connectionSet )
		throws Exception
	{
		logger.info( "Start SendStaticIPSettings" );
		String message = "";
		ServerContext serverContext = ServerContext.get();
		InetServService inetServService = serverContext.getService(InetServService.class, INET_MID);
		List<InetServ> listInetServ = inetServService.inetServList(CONTRACT_ID, null);
		for (InetServ inetServ :listInetServ) {
			switch (inetServ.getTypeTitle()) {
				case "White-IP":
					ResourceService resourceService = serverContext.getService(ResourceService.class, INET_MID);
					IpResource ipResource = resourceService.ipResourceGet(inetServ.	getIpResourceId());
					String ipAddress = InetAddress.getByAddress(inetServ.getAddressFrom()).getHostAddress();
					String subnetMask = ipResource.getSubnetMask();
					String router = ipResource.getRouter();
					String dns = ipResource.getDns();
					message = message + String.format("\nIP: %s\nNM: %s\nGW: %s\nDNS: %s\n", ipAddress, subnetMask,
							router, dns);
			}
		}
		if (message != "") {
			Connection connection = connectionSet.getConnection();
			ContractParameterManager contractParameterManager = new ContractParameterManager(connection);
			String email = contractParameterManager.getEmailFromParam(CONTRACT_ID, EMAIL_PID);
			if (email == null) {
				print("Не указан email в договоре");
				logger.info("Email not specified in contract");
			} else {
				ContractUtils contractUtils = new ContractUtils(connection);
				String contractTitle = contractUtils.getContractTitle(CONTRACT_ID);
				String subject = contractTitle + ". Настройки статического IP-адреса.";
				logger.info(String.format("Send email to %s | Subject: %s | Text: %s", email, subject, message));
				MailMsg mailMsg = new MailMsg(setup);
				mailMsg.sendMessage(email, subject, message);
			}
		}
		logger.info( "Stop SendStaticIPSettings" );
	}
}