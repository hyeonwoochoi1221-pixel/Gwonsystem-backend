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
 static final String URL="jdbc:postgresql://127.0.0.1:55483/postgres?sslmode=disable";
 static Path work; static String role="isolated_app";
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS "+name);}
 static Connection admin()throws Exception{return DriverManager.getConnection(URL,"isolated_admin","");}
 static String scalar(Connection c,String sql)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
 static void script(Connection c,String sql)throws Exception{
  try(var s=c.createStatement()) {boolean rows=s.execute(sql); do {
   if(rows)try(var r=s.getResultSet()){var m=r.getMetaData();while(r.next()){StringJoiner out=new StringJoiner(" | ");for(int i=1;i<=m.getColumnCount();i++)out.add(m.getColumnLabel(i)+"="+r.getString(i));System.out.println(out);}}
   rows=s.getMoreResults();
  }while(rows||s.getUpdateCount()!=-1);}
 }
 static String shape(Connection c)throws Exception{
  var out=new StringBuilder();
  for(String q:List.of(
   "SELECT table_name,column_name,data_type,is_nullable,column_default,is_identity FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position",
   "SELECT conrelid::regclass,conname,pg_get_constraintdef(oid),convalidated,condeferrable FROM pg_constraint WHERE connamespace='public'::regnamespace ORDER BY conrelid,conname",
   "SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY tablename,indexname",
   "SELECT relname,relowner,relacl,relrowsecurity,relforcerowsecurity FROM pg_class WHERE relnamespace='public'::regnamespace ORDER BY relname",
   "SELECT tablename,policyname,roles,cmd,qual,with_check FROM pg_policies WHERE schemaname='public' ORDER BY tablename,policyname",
   "SELECT sequencename,data_type,start_value,min_value,max_value,increment_by,cycle FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename")){
   try(var s=c.createStatement();var r=s.executeQuery(q)){while(r.next()){for(int i=1;i<=r.getMetaData().getColumnCount();i++)out.append(r.getString(i)).append('|');out.append('\n');}}
  }return out.toString();
 }
 static void expected(Connection c,String sql,String state)throws Exception{
  try(var s=c.createStatement()){s.execute(sql);throw new AssertionError("Expected "+state);}
  catch(SQLException e){check(e.getSQLState().equals(state),"negative SQL "+state);System.out.println(e.getMessage());}
 }
 static org.hibernate.SessionFactory factory(){
  var registry=new StandardServiceRegistryBuilder().applySettings(Map.of(
   "hibernate.connection.url",URL,"hibernate.connection.username",role,"hibernate.connection.password","",
   "hibernate.hbm2ddl.auto","validate","hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect",
   "hibernate.physical_naming_strategy",CamelCaseToUnderscoresNamingStrategy.class.getName())).build();
  try {var sources=new MetadataSources(registry);for(Class<?> type:List.of(CompanyEvent.class,GalleryPhoto.class,Banner.class,Member.class,Notice.class,TestUser.class))sources.addAnnotatedClass(type);
   return sources.buildMetadata().buildSessionFactory();
  }catch(RuntimeException e){StandardServiceRegistryBuilder.destroy(registry);throw e;}
 }
 public static void main(String[] args)throws Exception{
  work=Path.of(args[0]);var sqlDir=work.getParent();
  check(org.hibernate.Version.getVersionString().equals("6.6.53.Final"),"Hibernate version");
  check(org.postgresql.Driver.getVersion().contains("42.7.11"),"pgJDBC version");
  try(var c=admin()){
   check(scalar(c,"SHOW server_version").startsWith("17.11"),"PostgreSQL17.11");
   check(scalar(c,"SELECT host(inet_server_addr())||':'||inet_server_port()").equals("127.0.0.1:55483"),"local endpoint");
   script(c,"CREATE ROLE isolated_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS; CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role; CREATE TABLE public.users(id bigint PRIMARY KEY); INSERT INTO public.users VALUES(42);");
   for(String t:List.of("members","notices","company_events","gallery_photos","home_banners","test_users"))script(c,"CREATE TABLE public."+t+"(id bigint PRIMARY KEY); INSERT INTO public."+t+" VALUES(7)");
   script(c,Files.readString(sqlDir.resolve("01-preflight.readonly.sql")));
   script(c,Files.readString(sqlDir.resolve("02-backup-manifest.readonly.sql")));
   String baseline=shape(c);String original=Files.readString(sqlDir.resolve("03-rebuild.DESTRUCTIVE-DRAFT.sql"));
   expected(c,original,"P0001");script(c,"ROLLBACK");check(shape(c).equals(baseline),"original blocked without changes");
   String copy=original.replace("REPLACE_APPROVED_PRODUCTION_REF","abcdefghijklmnopqrst").replace("REPLACE_PRODUCTION_APP_ROLE",role).replace("boolean := false","boolean := true");
   Files.writeString(work.resolve("rebuild-rollback.TEST-ONLY.sql"),copy);script(c,copy);
   check(shape(c).equals(baseline),"full rebuild ROLLBACK restores schema");
   check(scalar(c,"SELECT count(*) FROM public.members WHERE id=7").equals("1"),"ROLLBACK preserves fixture data");
   copy=copy.replace("\nROLLBACK;","\nCOMMIT;");Files.writeString(work.resolve("rebuild-commit.TEST-ONLY.sql"),copy);script(c,copy);
   script(c,Files.readString(sqlDir.resolve("04-postflight.readonly.sql")).replace("REPLACE_PRODUCTION_APP_ROLE",role));
   check(scalar(c,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name<>'users'").equals("63"),"63 columns");
   check(scalar(c,"SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND conrelid<>'public.users'::regclass").equals("12"),"12 PK UNIQUE FK CHECK constraints");
   check(scalar(c,"SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename<>'users'").equals("11"),"11 indexes");
   check(scalar(c,"SELECT count(*) FROM pg_sequences WHERE schemaname='public'").equals("6"),"6 identity sequences");
   check(scalar(c,"SELECT count(*) FROM public.users WHERE id=42").equals("1"),"users retained");
   var before=shape(c);Files.writeString(work.resolve("schema-before-validate.txt"),before);
   try(var sf=factory()){check(true,"six actual entities Hibernate validate");}
   check(before.equals(shape(c)),"validate leaves schema unchanged");Files.writeString(work.resolve("schema-after-validate.txt"),shape(c));
   try(var app=DriverManager.getConnection(URL,role,"")){
    expected(app,"CREATE TABLE public.denied(id int)","42501");
    expected(app,"ALTER TABLE public.members ADD COLUMN denied int","42501");
    expected(app,"DROP TABLE public.members","42501");
    script(app,"INSERT INTO public.members(username,email,last_name,first_name) VALUES('writer','writer@example.test','Test','Writer')");
    expected(app,"INSERT INTO public.notices(title,content) VALUES('x','x')","23502");
    expected(app,"INSERT INTO public.notices(author_member_id,title,content) VALUES(-1,'x','x')","23503");
   }
   var ds=new DriverManagerDataSource(URL,role,"");var jdbc=new JdbcTemplate(ds);
   Long memberId=jdbc.queryForObject("SELECT id FROM public.members WHERE username='writer'",Long.class);
   Member member=new Member();member.setId(memberId);member.setUsername("writer");member.setRole(Role.ROLE_SUPERVISOR);
   var repo=(MemberRepository)java.lang.reflect.Proxy.newProxyInstance(Probe.class.getClassLoader(),new Class[]{MemberRepository.class},(p,m,a)->{
    if(m.getName().equals("findByUsername"))return Optional.of(member);throw new UnsupportedOperationException(m.getName());});
   var policy=new ProdNoticeAuthorPolicy(repo,jdbc);policy.afterPropertiesSet();
   var auth=new UsernamePasswordAuthenticationToken("writer",null,List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR")));
   var dto=new NoticeCreateRequestDto();dto.setAuthorId(999L);
   var author=policy.resolve(dto,auth);check(author.memberId().equals(memberId)&&author.legacyUserId()==null,"spoofed author ignored");
   try{policy.resolve(dto,null);throw new AssertionError();}catch(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException e){check(true,"unauthenticated rejected");}
   member.setRole(Role.ROLE_ASSOCIATE);try{policy.resolve(dto,auth);throw new AssertionError();}catch(org.springframework.security.access.AccessDeniedException e){check(true,"associate rejected despite token supervisor");}
   member.setRole(Role.ROLE_SUPERVISOR);
   Long id=policy.create(Notice.builder().title("isolated").content("fixture only").category("test").build(),author);
   check(jdbc.queryForObject("SELECT author_member_id FROM public.notices WHERE id=?",Long.class,id).equals(memberId),"real policy JDBC insert");
   check(jdbc.queryForObject("SELECT author_id IS NULL FROM public.notices WHERE id=?",Boolean.class,id),"legacy author NULL");
   try(var app=ds.getConnection()){expected(app,"DELETE FROM public.members WHERE id="+memberId,"23503");}
   try(var sf=factory();var session=sf.openSession()){
    session.beginTransaction();Notice n=session.find(Notice.class,id);n.increaseViewCount();session.getTransaction().commit();
    check(jdbc.queryForObject("SELECT view_count FROM public.notices WHERE id=?",Integer.class,id)==1,"Hibernate detail view count");
    session.beginTransaction();session.remove(n);session.getTransaction().commit();
    check(jdbc.queryForObject("SELECT count(*) FROM public.notices WHERE id=?",Integer.class,id)==0,"Hibernate delete");
   }
   check(before.equals(shape(c)),"DML schema unchanged");
   expected(c,copy,"P0001");script(c,"ROLLBACK");check(before.equals(shape(c)),"repeat rebuild refused");
   // Isolated fixture-only experiment: restore types afterwards; never prescribe production conversion.
   script(c,"BEGIN; ALTER TABLE public.members ALTER COLUMN privacy_agreed_at TYPE timestamptz, ALTER COLUMN created_at TYPE timestamptz, ALTER COLUMN updated_at TYPE timestamptz; ALTER TABLE public.notices ALTER COLUMN created_at TYPE timestamptz, ALTER COLUMN updated_at TYPE timestamptz; ALTER TABLE public.test_users ALTER COLUMN created_at TYPE timestamptz; COMMIT;");
   String timeShape=shape(c);
   try(var sf=factory()){check(true,"LocalDateTime with timestamptz validate");}
   catch(Exception e){
    System.err.println("FAIL LocalDateTime with timestamptz validate: "+e);
    throw e; // Uncaught by main: validation failures must produce a nonzero exit code.
   }
   check(timeShape.equals(shape(c)),"timestamptz validate no DDL");
   try(var r=c.getMetaData().getColumns(null,"public","members","created_at")){while(r.next())System.out.println("JDBC DATA_TYPE="+r.getInt("DATA_TYPE")+" TYPE_NAME="+r.getString("TYPE_NAME"));}
   script(c,"ALTER TABLE public.members ALTER COLUMN privacy_agreed_at TYPE timestamp, ALTER COLUMN created_at TYPE timestamp, ALTER COLUMN updated_at TYPE timestamp; ALTER TABLE public.notices ALTER COLUMN created_at TYPE timestamp, ALTER COLUMN updated_at TYPE timestamp; ALTER TABLE public.test_users ALTER COLUMN created_at TYPE timestamp;");
   check(before.equals(shape(c)),"time experiment restored original structure");
  }
 }
}
