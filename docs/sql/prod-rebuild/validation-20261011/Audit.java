import java.sql.*;
import java.nio.file.*;
import java.util.*;
public class Audit {
 public static void main(String[] args)throws Exception{
  Path work=Path.of(args[0]);
  try(var c=Probe.admin()){
   Probe.check(Probe.shape(c).equals(Files.readString(work.resolve("schema-before-validate.txt"))),"complete schema snapshot retained after all tests");
   Probe.check(Probe.scalar(c,"SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relkind='r' AND relname<>'users' AND relrowsecurity AND relforcerowsecurity").equals("6"),"all six tables RLS enabled/forced");
   Probe.check(Probe.scalar(c,"SELECT count(*) FROM pg_policies WHERE schemaname='public' AND roles=ARRAY['isolated_app']::name[] AND cmd='ALL' AND qual='true' AND with_check='true'").equals("6"),"six backend-only policies");
   Probe.check(Probe.scalar(c,"SELECT count(*) FROM pg_sequences WHERE schemaname='public' AND data_type='bigint'::regtype AND increment_by=1 AND start_value=1 AND NOT cycle").equals("6"),"bigint identity sequence configuration");
   Probe.check(Probe.scalar(c,"SELECT count(*) FROM pg_depend d JOIN pg_class s ON s.oid=d.objid JOIN pg_class t ON t.oid=d.refobjid WHERE s.relkind='S' AND t.relnamespace='public'::regnamespace AND d.deptype='i'").equals("6"),"six identity-owned sequences");
   Probe.check(Probe.scalar(c,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND ((table_name='members' AND column_name IN ('role','terms_agreed','privacy_agreed','marketing_agreed')) OR (table_name='notices' AND column_name IN ('view_count','is_pinned'))) AND column_default IS NOT NULL").equals("6"),"six explicit defaults");
   Probe.check(Probe.scalar(c,"SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND contype='f' AND convalidated AND NOT condeferrable").equals("2"),"both FKs validated nondeferrable");
   for(String t:List.of("members","notices","company_events","gallery_photos","home_banners","test_users")){
    for(String permission:List.of("SELECT","INSERT","UPDATE","DELETE"))
     Probe.check(Probe.scalar(c,"SELECT has_table_privilege('isolated_app','public."+t+"','"+permission+"')").equals("t"),t+" "+permission);
    Probe.check(Probe.scalar(c,"SELECT has_sequence_privilege('isolated_app',pg_get_serial_sequence('public."+t+"','id'),'USAGE')").equals("t"),t+" sequence usage");
    for(String r:List.of("anon","authenticated","service_role"))
     Probe.check(Probe.scalar(c,"SELECT has_table_privilege('"+r+"','public."+t+"','SELECT,INSERT,UPDATE,DELETE')").equals("f"),t+" denied "+r);
   }
  }
  try(var app=DriverManager.getConnection(Probe.URL,"isolated_app","")){
   Probe.script(app,"BEGIN; LOCK TABLE public.home_banners IN EXCLUSIVE MODE; ROLLBACK;");
   Probe.check(true,"banner native lock allowed without DDL permission");
   Probe.expected(app,"INSERT INTO public.members(username,email,last_name,first_name) VALUES('writer','other@example.test','x','y')","23505");
   Probe.expected(app,"INSERT INTO public.members(username,email,last_name,first_name,role) VALUES('other','other@example.test','x','y','ADMIN')","23514");
  }
  try(var anon=Probe.admin()){
   Probe.script(anon,"SET ROLE anon");
   Probe.expected(anon,"SELECT count(*) FROM public.members","42501");
  }
  try(var c=Probe.admin()){
   Probe.script(c,"BEGIN; INSERT INTO public.notices(author_id,author_member_id,title,content) SELECT 42,id,'legacy-test','fixture' FROM public.members WHERE username='writer'; DELETE FROM public.users WHERE id=42;");
   Probe.check(Probe.scalar(c,"SELECT count(*) FROM public.notices WHERE title='legacy-test' AND author_id IS NULL").equals("1"),"legacy FK ON DELETE SET NULL");
   Probe.script(c,"ROLLBACK");
   Probe.check(Probe.scalar(c,"SELECT count(*) FROM public.users WHERE id=42").equals("1"),"legacy FK fixture rollback preserved users");
   Probe.check(Probe.shape(c).equals(Files.readString(work.resolve("schema-before-validate.txt"))),"audit schema unchanged");
  }
 }
}
