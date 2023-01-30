package ru.bitel.bgbilling.scripts.services.custom.fialka;

import ru.bitel.bgbilling.kernel.event.Event;
import ru.bitel.bgbilling.kernel.script.server.dev.EventScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;
import org.apache.log4j.Logger;
import ru.bitel.bgbilling.kernel.event.events.AdditionalActionEvent;
import ru.bitel.bgbilling.kernel.event.events.GetAdditionalActionListEvent;
import ru.bitel.bgbilling.common.BGException;
import bitel.billing.server.util.MailMsg;
import java.sql.Connection;
import bitel.billing.server.contract.bean.ContractUtils;
import bitel.billing.server.contract.bean.ContractParameterManager;
import ru.bitel.bgbilling.kernel.container.managed.ServerContext;
import ru.bitel.bgbilling.modules.inet.api.common.service.InetServService;
import ru.bitel.bgbilling.modules.inet.api.common.service.InetDeviceService;
import ru.bitel.bgbilling.modules.inet.api.common.bean.InetServ;
import ru.bitel.oss.systems.inventory.resource.common.ResourceService;
import ru.bitel.oss.systems.inventory.resource.common.bean.IpResource;
import java.net.InetAddress;
import java.util.*;

public class ContractAdditionalActions
	extends EventScriptBase<Event> {
	private static final int EMAIL_PID = 13;
	private static final int INET_MID = 15;
	private static final Logger logger = Logger.getLogger(ContractAdditionalActions.class);

	@Override
	public void onEvent( Event event, Setup setup, ConnectionSet connectionSet )
		throws Exception {
		if (event instanceof GetAdditionalActionListEvent) {
			onGetAdditionalActionListEvent((GetAdditionalActionListEvent) event);
		} else if (event instanceof AdditionalActionEvent) {
			onAdditionalActionEvent((AdditionalActionEvent) event, setup, connectionSet);
		}
	}

	private void onGetAdditionalActionListEvent(GetAdditionalActionListEvent event) {
		event.addAction(1, "Отправить настройки статического IP-адреса на email", "");
	}

	private void onAdditionalActionEvent(AdditionalActionEvent event, Setup setup, ConnectionSet connectionSet)
			throws Exception {
		switch (event.getActionId()) {
			case 1:
				staticConfigEmail(event, setup, connectionSet);
				break;
			default:
				return;
		}
	}

	private void staticConfigEmail(AdditionalActionEvent event, Setup setup, ConnectionSet connectionSet)
			throws Exception {
		String message = "";
		ServerContext serverContext = ServerContext.get();
		InetServService inetServService = serverContext.getService(InetServService.class, INET_MID);
		int cid = event.getContractId();
		List<InetServ> listInetServ = inetServService.inetServList(cid, null);
		for (InetServ inetServ :listInetServ) {
			switch (inetServ.getTypeTitle()) {
				case "White-IP":
					ResourceService resourceService = serverContext.getService(ResourceService.class, INET_MID);
					IpResource ipResource = resourceService.ipResourceGet(inetServ.	getIpResourceId());
					String ipAddress = InetAddress.getByAddress(inetServ.getAddressFrom()).getHostAddress();
					String subnetMask = ipResource.getSubnetMask();
					String router = ipResource.getRouter();
					String dns = ipResource.getDns();
					message = message + String.format(
							"\nAдрес (Address): %s\nМаска сети (Netmask): %s\nШлюз (Gateway): %s\nDNS: %s\n",
							ipAddress, subnetMask, router, dns);
			}
		}
		if (message != "") {
			Connection connection = connectionSet.getConnection();
			ContractParameterManager contractParameterManager = new ContractParameterManager(connection);
			String email = contractParameterManager.getEmailFromParam(cid, EMAIL_PID);
			if (email == null) {
				event.addReport("Не указан email в договоре");
				logger.info("Email not specified in contract");
			} else {
				ContractUtils contractUtils = new ContractUtils(connection);
				String contractTitle = contractUtils.getContractTitle(cid);
				String subject = "Договор " + contractTitle + ". Настройки статического IP-адреса.";
				logger.info(String.format("Send static IP config to %s | Subject: %s | Text: %s", email, subject, message));
				MailMsg mailMsg = new MailMsg(setup);
				mailMsg.sendMessage(email, subject, message);
				event.addReport("Настройки статического IP-адреса отправлены на " + email);
			}
		}
	}
}