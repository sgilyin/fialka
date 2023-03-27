package ru.bitel.bgbilling.scripts.services.custom.fialka;

import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;
import java.sql.Connection;
import org.apache.log4j.Logger; //Для работы логов
import java.net.HttpURLConnection; //Для отправки запросов
import java.net.URL; //Для отправки запросов
import java.io.OutputStream; //Для работы с запросами
import java.sql.PreparedStatement; //Для работы с SQL
import java.sql.ResultSet; //Для работы с SQL
import java.sql.Timestamp; //Для работы с timestamp

public class MessengerNotifyer extends GlobalScriptBase {
    String Message = "Чат бот:\r\nТекст сообщения"; // Сообщение для уведомления

    final int TG_PID = 54; // ID параметра договора со списком аккаунтов Телеграма для уведомлений
    final int VK_PID = 55; // ID параметра договора со списком аккаунтов вконтакте для уведомлений
    String VK_API_VERSION = "5.131"; // Версия АПИ вконтакте
    private static final Logger logger = Logger.getLogger( MessengerNotifyer.class );

    String QUERY = "SELECT t_cpt1.pid,t_cpt1.val uid\n" +
            "FROM contract AS t_c\n" +
            "LEFT JOIN contract_parameter_type_1 t_cpt1 ON t_cpt1.cid=t_c.id AND t_cpt1.pid IN (54,55) \n" +
            "LEFT JOIN contract_parameter_type_2 t_cpt2 ON t_cpt2.cid=t_c.id\n" +
            "WHERE t_c.status=0 AND NOT t_cpt1.val IS NULL";

    @Override
    public void execute( Setup setup, ConnectionSet connectionSet ) throws Exception {
        logger.info("BEGIN");
        Connection connection = connectionSet.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement( QUERY );
        ResultSet resultSet = preparedStatement.executeQuery();
        while (resultSet.next()) {
            int pid = resultSet.getInt(1);
            String uids = resultSet.getString(2);
            String[] subStr;
            String delimeter = ";";
            subStr = uids.split(delimeter);
            for(int i = 0; i < subStr.length; i++) {
                sendMessage( pid, subStr[i], Message);
            }
        }
        logger.info("END");
    }

    public void sendMessage( int msngr, String uid, String msg ) throws Exception {
        Setup setup = Setup.getSetup();
        URL url = new URL("https://fialka.tv");
        String post = "";
        switch (msngr) {
            case TG_PID:
                url = new URL("https://api.telegram.org/bot" + setup.get("bot.token.tg") + "/sendMessage");
                post = "chat_id=" + uid + "&text=" + msg;
                break;
            case VK_PID:
                url = new URL("https://api.vk.com/method/messages.send");
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                post = "user_id=" + uid + "&message=" + msg + "&v=" + VK_API_VERSION + "&access_token=" + setup.get("bot.token.vk") + "&random_id=" + timestamp.getTime();
                break;
        }
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setRequestProperty("User-Agent", "curl/7.52.1");
        httpURLConnection.setDoOutput(true);
        OutputStream outputStream = httpURLConnection.getOutputStream();
        outputStream.write(post.getBytes());
        outputStream.flush();
        outputStream.close();
        int responseCode = httpURLConnection.getResponseCode();
        String log = "%s|%s|%s";
        Object[] dataLog = { msngr, uid, responseCode };
        String msgLog = String.format(log, dataLog);
        print(msgLog);
        logger.info(msgLog);
    }
}
