import java.nio.file.*;
import java.sql.*;
public class Check {
 public static void main(String[] args)throws Exception{
  Path work=Path.of(args[0]);
  try(var c=DriverManager.getConnection(Probe.URL,"isolated_admin","")){
   Probe.check(Path.of(Probe.scalar(c,"SHOW data_directory")).toRealPath().equals(work.resolve("pgdata-final").toRealPath()),"final isolated directory");
   Probe.check(Probe.scalar(c,"SELECT host(inet_server_addr())||':'||inet_server_port()").equals("127.0.0.1:55489"),"localhost only");
   String before=Probe.snapshot(c);
   for(String name:new String[]{"01-preflight.readonly.sql","02-backup-manifest.readonly.sql","04-postflight.readonly.sql"}){
    Probe.sql(c,Files.readString(work.getParent().resolve(name)).replace("REPLACE_PRODUCTION_APP_ROLE","isolated_app"));
    Probe.check(true,"current readonly SQL executed: "+name);
   }
   Probe.check(before.equals(Probe.snapshot(c)),"current readonly SQL leaves schema unchanged");
   Probe.check(before.equals(Files.readString(work.resolve("schema-after-validate.txt"))),"final schema matches validated snapshot");
   System.out.println("TOTAL PASS "+Probe.assertions);
  }
 }
}
