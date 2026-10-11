import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.hibernate.boot.*;
import org.hibernate.boot.registry.*;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import com.gwonsystem.backend.entity.*;
import com.gwonsystem.backend.service.*;
import com.gwonsystem.backend.repository.MemberRepository;
import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class Probe {
 static final String URL="jdbc:postgresql://127.0.0.1:55497/postgres?sslmode=disable";
 static Path work; static final String ROLE="gwon_prod_app"; static int assertions;
 static final List<String> TABLES=List.of("members","notices","company_events","gallery_photos","home_banners","test_users");
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);assertions++;System.out.println("PASS "+label);}
 static String scalar(Connection c,String q)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(q)){r.next();return r.getString(1);}}
 static void sql(Connection c,String q)throws Exception{try(var s=c.createStatement()){s.execute(q);}}
 static void denied(Connection c,String q,String state)throws Exception{
  try{sql(c,q);throw new AssertionError("Expected "+state);}catch(SQLException e){check(state.equals(e.getSQLState()),"expected SQLSTATE "+state);System.out.println(e.getMessage());}
 }
 static String snapshot(Connection c)throws Exception{
  var out=new StringBuilder();
  for(String q:List.of(
   "SELECT table_schema,table_name,column_name,data_type,character_maximum_length,is_nullable,column_default,is_identity,identity_generation FROM information_schema.columns WHERE table_schema IN ('public','auth','storage') ORDER BY 1,2,ordinal_position",
   "SELECT conrelid::regclass,conname,pg_get_constraintdef(oid),convalidated,condeferrable FROM pg_constraint WHERE connamespace='public'::regnamespace ORDER BY 1,2",
   "SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY 1,2",
   "SELECT relname,relowner,relacl,relrowsecurity,relforcerowsecurity FROM pg_class WHERE relnamespace='public'::regnamespace ORDER BY 1",
   "SELECT tablename,policyname,roles,cmd,qual,with_check FROM pg_policies WHERE schemaname='public' ORDER BY 1,2",
   "SELECT sequencename,data_type,start_value,min_value,max_value,increment_by,cycle FROM pg_sequences WHERE schemaname='public' ORDER BY 1",
   "SELECT evtname,evtowner,evtevent,evtenabled,evtfoid,evttags,md5(pg_get_functiondef(evtfoid)) FROM pg_event_trigger ORDER BY 1",
   "SELECT defaclrole,defaclnamespace,defaclobjtype,defaclacl FROM pg_default_acl ORDER BY 1,2,3")){
   try(var s=c.createStatement();var r=s.executeQuery(q)){while(r.next()){for(int i=1;i<=r.getMetaData().getColumnCount();i++)out.append(r.getString(i)).append('|');out.append('\n');}}
  }return out.toString();
 }
 static String events(Connection c)throws Exception{return scalar(c,"SELECT string_agg(evtname||':'||evtowner||':'||evtevent||':'||evtenabled::text||':'||evtfoid||':'||md5(pg_get_functiondef(evtfoid)),',' ORDER BY evtname) FROM pg_event_trigger");}
 static org.hibernate.SessionFactory factory(){
  var reg=new StandardServiceRegistryBuilder().applySettings(Map.of("hibernate.connection.url",URL,"hibernate.connection.username",ROLE,"hibernate.connection.password","","hibernate.hbm2ddl.auto","validate","hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect","hibernate.physical_naming_strategy",CamelCaseToUnderscoresNamingStrategy.class.getName())).build();
  try{var sources=new MetadataSources(reg);for(var type:List.of(CompanyEvent.class,GalleryPhoto.class,Banner.class,Member.class,Notice.class,TestUser.class))sources.addAnnotatedClass(type);return sources.buildMetadata().buildSessionFactory();}
  catch(RuntimeException e){StandardServiceRegistryBuilder.destroy(reg);throw e;}
 }
 static void blocked(Connection c,String script,String shape)throws Exception{denied(c,script,"P0001");sql(c,"ROLLBACK");check(shape.equals(snapshot(c)),"blocked transaction leaves structure unchanged");}
 public static void main(String[] args)throws Exception{
  work=Path.of(args[0]);var root=work.getParent();
  check(org.hibernate.Version.getVersionString().equals("6.6.53.Final"),"Hibernate 6.6.53.Final");
  check(org.postgresql.Driver.getVersion().contains("42.7.11"),"pgJDBC 42.7.11");
  try(var c=DriverManager.getConnection(URL,"isolated_admin","")){
   check(scalar(c,"SHOW server_version").startsWith("17.11"),"PostgreSQL 17.11");
   check(scalar(c,"SELECT host(inet_server_addr())||':'||inet_server_port()").equals("127.0.0.1:55497"),"local-only endpoint");
   check(Path.of(scalar(c,"SHOW data_directory")).toRealPath().equals(work.resolve("pgdata-89-7").toRealPath()),"new exact pgdata");
   boolean fresh=scalar(c,"SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relkind IN ('r','S')").equals("0");
   if(!fresh){check(scalar(c,"SELECT count(*) FROM public.members WHERE id=7 AND username='old'").equals("1") && scalar(c,"SELECT count(*) FROM pg_event_trigger").equals("6") && scalar(c,"SELECT count(*) FROM pg_attribute WHERE attrelid='public.notices'::regclass AND attname='author_member_id' AND NOT attisdropped").equals("0"),"resume only this step pre-rebuild fixture");}
   else check(true,"empty new public schema");
   String createRole=Files.readString(root.resolve("app-role/01-create-prod-app-role.DRAFT.sql"));
   denied(c,createRole,"P0001");sql(c,"ROLLBACK");
   check(scalar(c,"SELECT count(*) FROM pg_roles WHERE rolname='gwon_prod_app'").equals("0"),"unapproved role creation blocked");
   String approvedRole=createRole.replace("REPLACE_APPROVED_PRODUCTION_REF","zsaqpsohhwygrawphnsg").replace("boolean := false","boolean := true");
   sql(c,"CREATE SCHEMA net; CREATE TABLE net.http_request_queue(id int); GRANT USAGE ON SCHEMA net TO PUBLIC; GRANT SELECT ON net.http_request_queue TO PUBLIC");
   denied(c,approvedRole,"P0001");sql(c,"ROLLBACK");
   check(scalar(c,"SELECT count(*) FROM pg_roles WHERE rolname='gwon_prod_app'").equals("0"),"PUBLIC net exposure blocks provisioning atomically");
   sql(c,"DROP TABLE net.http_request_queue; DROP SCHEMA net");
   sql(c,"CREATE FUNCTION public.fixture_definer() RETURNS int LANGUAGE sql SECURITY DEFINER AS 'SELECT 1'");
   denied(c,approvedRole,"P0001");sql(c,"ROLLBACK");sql(c,"DROP FUNCTION public.fixture_definer()");
   check(scalar(c,"SELECT count(*) FROM pg_roles WHERE rolname='gwon_prod_app'").equals("0"),"callable SECURITY DEFINER blocks provisioning");
   sql(c,approvedRole);check(scalar(c,"SELECT count(*) FROM pg_roles WHERE rolname='gwon_prod_app'").equals("0"),"approved provisioning ROLLBACK leaves no role");
   sql(c,"CREATE ROLE fixture_creator LOGIN CREATEROLE NOSUPERUSER NOCREATEDB NOBYPASSRLS NOREPLICATION; ALTER DATABASE postgres OWNER TO fixture_creator; ALTER SCHEMA public OWNER TO fixture_creator");
   try(var creator=DriverManager.getConnection(URL,"fixture_creator","")){
    check(scalar(creator,"SELECT NOT rolsuper AND rolcreaterole FROM pg_roles WHERE rolname=current_user").equals("t"),"non-superuser creator with CREATEROLE");
    sql(creator,approvedRole.replace("\nROLLBACK;","\nCOMMIT;"));
    check(true,"role script succeeds under non-superuser schema/database owner");
   }
   check(scalar(c,"SELECT NOT rolinherit AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole FROM pg_roles WHERE rolname='gwon_prod_app'").equals("t"),"least privilege NOINHERIT role created");
   String original=Files.readString(root.resolve("03-rebuild.DESTRUCTIVE-DRAFT.sql"));
   String[][] defs={{"issue_graphql_placeholder","sql_drop","set_graphql_placeholder"},{"pgrst_ddl_watch","ddl_command_end","pgrst_ddl_watch"},{"pgrst_drop_watch","sql_drop","pgrst_drop_watch"},{"issue_pg_cron_access","ddl_command_end","grant_pg_cron_access"},{"issue_pg_net_access","ddl_command_end","grant_pg_net_access"},{"issue_pg_graphql_access","ddl_command_end","grant_pg_graphql_access"}};
   if(fresh){
   sql(c,"CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role; CREATE ROLE supabase_admin SUPERUSER NOLOGIN; CREATE SCHEMA extensions; CREATE SCHEMA fixture_audit; CREATE TABLE fixture_audit.events(name text,event text,tag text); CREATE SCHEMA auth; CREATE TABLE auth.sentinel(id int PRIMARY KEY); INSERT INTO auth.sentinel VALUES(1); CREATE SCHEMA storage; CREATE TABLE storage.sentinel(id int PRIMARY KEY); INSERT INTO storage.sentinel VALUES(1); CREATE TABLE public.users(id integer PRIMARY KEY); INSERT INTO public.users VALUES(42);");
   String ddl=original.substring(original.indexOf("CREATE TABLE public.members"),original.indexOf("-- Preserve reviewed non-ID defaults"));
   ddl=ddl.replace(",author_member_id bigint NOT NULL","").replace(",\n CONSTRAINT notices_author_member_id_fkey FOREIGN KEY(author_member_id) REFERENCES public.members(id)\n ON DELETE RESTRICT ON UPDATE NO ACTION NOT DEFERRABLE","");
   ddl=ddl.replace("CREATE INDEX notices_author_member_id_idx ON public.notices(author_member_id);","").replace("CREATE INDEX notices_author_id_idx ON public.notices(author_id);","");
   sql(c,ddl);
   // Only local fixture defaults; real expressions still require production review.
   sql(c,"ALTER TABLE public.members ALTER COLUMN role SET DEFAULT 'ROLE_ASSOCIATE', ALTER COLUMN privacy_agreed_at SET DEFAULT now(), ALTER COLUMN created_at SET DEFAULT now(), ALTER COLUMN updated_at SET DEFAULT now(); ALTER TABLE public.notices ALTER COLUMN category SET DEFAULT 'fixture', ALTER COLUMN created_at SET DEFAULT now(), ALTER COLUMN updated_at SET DEFAULT now(); ALTER TABLE public.test_users ALTER COLUMN created_at SET DEFAULT now(); INSERT INTO public.members(id,username,email,last_name,first_name) VALUES(7,'old','old@example.test','Old','Fixture'); ALTER TABLE public.test_users ENABLE ROW LEVEL SECURITY; CREATE POLICY \"Allow public insert and select\" ON public.test_users FOR ALL TO PUBLIC USING(true) WITH CHECK(true); ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon,authenticated,service_role; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon,authenticated,service_role;");
   for(var d:defs){sql(c,"CREATE FUNCTION extensions."+d[2]+"() RETURNS event_trigger LANGUAGE plpgsql AS $$ BEGIN IF current_user='isolated_admin' THEN INSERT INTO fixture_audit.events VALUES('"+d[0]+"',TG_EVENT,TG_TAG); END IF; END $$; ALTER FUNCTION extensions."+d[2]+"() OWNER TO supabase_admin; CREATE EVENT TRIGGER "+d[0]+" ON "+d[1]+" EXECUTE FUNCTION extensions."+d[2]+"(); ALTER EVENT TRIGGER "+d[0]+" OWNER TO supabase_admin;");}
   }
   String baseline=snapshot(c),eventBefore=events(c);
   Files.writeString(work.resolve("fixture-before.txt"),baseline);
   sql(c,Files.readString(root.resolve("01-preflight.readonly.sql")));
   blocked(c,original,baseline);
   String approved=original.replace("REPLACE_APPROVED_PRODUCTION_REF","zsaqpsohhwygrawphnsg").replace("REPLACE_PRODUCTION_APP_ROLE",ROLE).replace("boolean := false","boolean := true");
   for(String flag:List.of("business_data_reset_approved","project_identity_verified","production_app_role_verified","backend_only_rls_approved","managed_event_triggers_reviewed","existing_defaults_reviewed","dependency_review_approved"))blocked(c,approved.replace(flag+" boolean := true",flag+" boolean := false"),baseline);
   blocked(c,approved.replace("approved_app_role text := 'gwon_prod_app'","approved_app_role text := 'postgres'"),baseline);
   blocked(c,approved.replace("approved_app_role text := 'gwon_prod_app'","approved_app_role text := 'missing_app'"),baseline);
   sql(c,"CREATE POLICY unexpected ON public.members USING(true)");blocked(c,approved,snapshot(c));sql(c,"DROP POLICY unexpected ON public.members");
   sql(c,"CREATE EVENT TRIGGER unexpected ON ddl_command_end EXECUTE FUNCTION extensions.pgrst_ddl_watch()");blocked(c,approved,snapshot(c));sql(c,"DROP EVENT TRIGGER unexpected");
   sql(c,"CREATE VIEW public.external_notice_view AS SELECT id FROM public.notices");denied(c,approved,"2BP01");sql(c,"ROLLBACK; DROP VIEW public.external_notice_view");check(baseline.equals(snapshot(c)),"external view dependency cannot bypass RESTRICT");
   Files.writeString(work.resolve("rebuild-rollback.TEST-ONLY.sql"),approved);sql(c,approved);
   check(baseline.equals(snapshot(c)),"complete ROLLBACK restores schema and ACL");
   check(scalar(c,"SELECT count(*) FROM public.members WHERE id=7").equals("1"),"ROLLBACK restores business fixture");
   check(eventBefore.equals(events(c)),"ROLLBACK preserves event triggers");
   String commit=approved.replace("\nROLLBACK;","\nCOMMIT;");Files.writeString(work.resolve("rebuild-commit.TEST-ONLY.sql"),commit);sql(c,commit);
   check(eventBefore.equals(events(c)),"COMMIT preserves event trigger definitions and owners");
   for(var d:defs)check(Integer.parseInt(scalar(c,"SELECT count(*) FROM fixture_audit.events WHERE name='"+d[0]+"'"))>0,"managed-event fixture fired "+d[0]);
   check(scalar(c,"SELECT count(*) FROM public.users WHERE id=42").equals("1"),"users data retained");
   check(scalar(c,"SELECT (SELECT count(*) FROM auth.sentinel)+(SELECT count(*) FROM storage.sentinel)").equals("2"),"auth/storage fixtures retained");
   check(scalar(c,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name<>'users'").equals("63"),"63 columns");
   check(scalar(c,"SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND conrelid<>'public.users'::regclass").equals("12"),"12 constraints: PK6 UNIQUE4 FK2");
   check(scalar(c,"SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename<>'users'").equals("12"),"12 indexes");
   check(scalar(c,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND is_identity='YES'").equals("3"),"3 identity IDs preserved");
   check(scalar(c,"SELECT count(*) FROM pg_sequences WHERE schemaname='public' AND data_type='integer'::regtype").equals("2"),"notices/test_users integer sequence types retained");
   check(scalar(c,"SELECT count(*) FROM pg_depend d JOIN pg_class s ON s.oid=d.objid JOIN pg_class t ON t.oid=d.refobjid WHERE s.relkind='S' AND t.relnamespace='public'::regnamespace AND d.deptype='a'").equals("3"),"3 nextval sequences owned by columns");
   check(scalar(c,"SELECT count(*) FROM pg_depend d JOIN pg_class s ON s.oid=d.objid JOIN pg_class t ON t.oid=d.refobjid WHERE s.relkind='S' AND t.relnamespace='public'::regnamespace AND d.deptype='i'").equals("3"),"3 identity sequences owned internally");
   check(scalar(c,"SELECT column_default FROM information_schema.columns WHERE table_schema='public' AND table_name='notices' AND column_name='category'").contains("fixture"),"existing category default preserved");
   check(scalar(c,"SELECT column_default FROM information_schema.columns WHERE table_schema='public' AND table_name='members' AND column_name='created_at'").equals("now()"),"existing timestamp default preserved");
   sql(c,Files.readString(root.resolve("04-postflight.readonly.sql")).replace("REPLACE_PRODUCTION_APP_ROLE",ROLE));
   String before=snapshot(c);Files.writeString(work.resolve("schema-before-validate.txt"),before);
   try(var sf=factory()){check(true,"all six actual JPA entities validate PASS");}
   check(before.equals(snapshot(c)),"validate performs no schema changes");
   try(var r=c.getMetaData().getColumns(null,"public","members","created_at")){r.next();System.out.println("JDBC DATA_TYPE="+r.getInt("DATA_TYPE")+" TYPE_NAME="+r.getString("TYPE_NAME"));}
   try(var app=DriverManager.getConnection(URL,ROLE,"")){
    denied(app,"CREATE SCHEMA denied_schema","42501");
    denied(app,"CREATE ROLE escalation","42501");
    denied(app,"ALTER ROLE gwon_prod_app SUPERUSER","42501");
    denied(app,"SET ROLE isolated_admin","42501");
    denied(app,"SET SESSION AUTHORIZATION isolated_admin","42501");
    denied(app,"GRANT isolated_admin TO gwon_prod_app","42501");
    check(scalar(app,"SELECT has_table_privilege('gwon_prod_app','public.members','SELECT WITH GRANT OPTION')").equals("f"),"no table grant option");
    denied(app,"TRUNCATE public.members","42501");
    denied(app,"SELECT setval(pg_get_serial_sequence('public.members','id'),1)","42501");
    denied(app,"SELECT * FROM public.users","42501");
    denied(app,"SELECT * FROM auth.sentinel","42501");
    denied(app,"SELECT * FROM storage.sentinel","42501");
    sql(app,"CREATE TEMP TABLE temp_allowed(id int); DROP TABLE temp_allowed");check(true,"inherited TEMP allowed without permanent ownership");
    sql(app,"SET row_security=off");denied(app,"SELECT * FROM public.members","42501");sql(app,"SET row_security=on");
    for(String t:List.of("company_events","gallery_photos","home_banners","test_users")){
      String values=switch(t){case "company_events" -> "(title,category,start_date,location,description) VALUES('crud','fixture','2026-01-01','fixture','fixture')";case "gallery_photos" -> "(title,description,content_type,image,created_at,created_by) VALUES('crud','fixture','image/png',decode('00','hex'),now(),'fixture')";case "home_banners" -> "(title,description,content_type,image,created_at,created_by) VALUES('crud','fixture','image/png',decode('00','hex'),now(),'fixture')";default -> "(username,email) VALUES('crud','crud@example.test')";};
      sql(app,"INSERT INTO public."+t+values);check(Integer.parseInt(scalar(app,"SELECT count(*) FROM public."+t))>0,"app insert/select "+t);
      sql(app,"UPDATE public."+t+" SET id=id");sql(app,"DELETE FROM public."+t);check(scalar(app,"SELECT count(*) FROM public."+t).equals("0"),"app update/delete "+t);
    }
    denied(app,"CREATE TABLE public.denied(id int)","42501");denied(app,"ALTER TABLE public.members ADD denied int","42501");denied(app,"DROP TABLE public.members","42501");
    sql(app,"INSERT INTO public.members(username,email,last_name,first_name,phone) VALUES('writer','writer@example.test','Test','Writer','010-test')");
    denied(app,"INSERT INTO public.members(username,email,last_name,first_name,phone) VALUES('other','other@example.test','x','y','010-test')","23505");
    denied(app,"INSERT INTO public.notices(title,content) VALUES('x','x')","23502");denied(app,"INSERT INTO public.notices(author_member_id,title,content) VALUES(-1,'x','x')","23503");
    sql(app,"BEGIN; LOCK TABLE public.home_banners IN EXCLUSIVE MODE; ROLLBACK");check(true,"banner lock without DDL privileges");
    for(String t:TABLES)check(scalar(app,"SELECT nextval(pg_get_serial_sequence('public."+t+"','id'))").matches("[0-9]+"),"sequence usable "+t);
   }
   for(String t:TABLES){check(scalar(c,"SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='public."+t+"'::regclass").equals("t"),"RLS enabled/forced "+t);
    for(String p:List.of("SELECT","INSERT","UPDATE","DELETE"))check(scalar(c,"SELECT has_table_privilege('gwon_prod_app','public."+t+"','"+p+"')").equals("t"),"app "+t+" "+p);
    for(String r:List.of("anon","authenticated","service_role"))check(scalar(c,"SELECT has_table_privilege('"+r+"','public."+t+"','SELECT,INSERT,UPDATE,DELETE')").equals("f"),"no broad access "+r+" "+t);
   }
   sql(c,"SET ROLE anon");denied(c,"SELECT * FROM public.test_users","42501");sql(c,"RESET ROLE");
   var jdbc=new JdbcTemplate(new DriverManagerDataSource(URL,ROLE,""));Long memberId=jdbc.queryForObject("SELECT id FROM public.members WHERE username='writer'",Long.class);
   Member member=new Member();member.setId(memberId);member.setUsername("writer");member.setRole(Role.ROLE_SUPERVISOR);
   var repo=(MemberRepository)java.lang.reflect.Proxy.newProxyInstance(Probe.class.getClassLoader(),new Class[]{MemberRepository.class},(p,m,a)->{if(m.getName().equals("findByUsername"))return Optional.of(member);throw new UnsupportedOperationException(m.getName());});
   var policy=new ProdNoticeAuthorPolicy(repo,jdbc);policy.afterPropertiesSet();
   var auth=new UsernamePasswordAuthenticationToken("writer",null,List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR")));var dto=new NoticeCreateRequestDto();dto.setAuthorId(999L);
   var author=policy.resolve(dto,auth);check(author.memberId().equals(memberId)&&author.legacyUserId()==null,"client author spoof ignored");
   Long id=policy.create(Notice.builder().title("isolated").content("fixture").build(),author);
   check(jdbc.queryForObject("SELECT author_id IS NULL AND author_member_id=? FROM public.notices WHERE id=?",Boolean.class,memberId,id),"real policy stores member and NULL legacy author");
   try(var app=DriverManager.getConnection(URL,ROLE,"")){denied(app,"DELETE FROM public.members WHERE id="+memberId,"23503");denied(app,"UPDATE public.notices SET author_id=-1 WHERE id="+id,"23503");}
   sql(c,"BEGIN; UPDATE public.notices SET author_id=42 WHERE id="+id+"; DELETE FROM public.users WHERE id=42");check(scalar(c,"SELECT author_id IS NULL FROM public.notices WHERE id="+id).equals("t"),"BIGINT legacy FK to INTEGER users SET NULL works");sql(c,"ROLLBACK");
   try(var sf=factory();var session=sf.openSession()){session.beginTransaction();var notice=session.find(Notice.class,id);notice.increaseViewCount();session.getTransaction().commit();check(jdbc.queryForObject("SELECT view_count FROM public.notices WHERE id=?",Integer.class,id)==1,"Hibernate notice detail view count");session.beginTransaction();session.remove(notice);session.getTransaction().commit();check(jdbc.queryForObject("SELECT count(*) FROM public.notices WHERE id=?",Integer.class,id)==0,"Hibernate notice delete");}
   blocked(c,commit,before);
   check(before.equals(snapshot(c)),"DML and all tests leave schema unchanged");Files.writeString(work.resolve("schema-after-validate.txt"),snapshot(c));
   String grants=Files.readString(root.resolve("app-role/02-grants-rls.DRAFT.sql"));
   blocked(c,grants,before);
   String approvedGrants=grants.replace("REPLACE_APPROVED_PRODUCTION_REF","zsaqpsohhwygrawphnsg").replace("boolean := false","boolean := true").replace("\nROLLBACK;","\nCOMMIT;");
   sql(c,approvedGrants);check(before.equals(snapshot(c)),"grant script reuses rebuild policies and changes no other ACL or RLS");
   sql(c,Files.readString(root.resolve("app-role/03-verify-prod-app-role.readonly.sql")));
   sql(c,Files.readString(root.resolve("app-role/04-production-dependencies.readonly.sql")));check(true,"extended metadata audit syntax PG17");
   sql(c,Files.readString(root.resolve("app-role/05-production-followup.readonly.sql")));check(true,"follow-up metadata audit syntax PG17");
   System.out.println("TOTAL PASS "+assertions);
  }
 }
}
