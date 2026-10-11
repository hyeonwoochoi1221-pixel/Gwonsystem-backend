package com.gwonsystem.backend.config;

import javax.sql.DataSource;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DevDataSourceGuardTest {
    Connection connection(String database,String role,boolean tls,boolean privileged,boolean create,boolean owner) throws Exception {
        var c=mock(Connection.class);var s=mock(Statement.class);var r=mock(ResultSet.class);
        var metadata=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(DevEnvironmentGuardTest.settings().get("spring.datasource.url").toString());
        when(c.getAutoCommit()).thenReturn(true);when(c.createStatement()).thenReturn(s);when(s.executeQuery(anyString())).thenReturn(r);
        when(r.next()).thenReturn(true);when(r.getString(1)).thenReturn(database);when(r.getString(2)).thenReturn(role);
        when(r.getBoolean(3)).thenReturn(tls);when(r.getBoolean(4)).thenReturn(privileged);
        when(r.getBoolean(5)).thenReturn(create);when(r.getBoolean(6)).thenReturn(owner);return c;
    }
    @Test void acceptsOnlyReadOnlyIdentityProbeAndRestoresTransactionState() throws Exception {
        var c=connection("postgres","gwon_dev_app",true,false,false,false);
        DevDataSourceGuard.verifyPeer(c,"postgres","gwon_dev_app");
        var order=inOrder(c);order.verify(c).setReadOnly(true);order.verify(c).setAutoCommit(false);
        order.verify(c).createStatement();order.verify(c).rollback();order.verify(c).setAutoCommit(true);order.verify(c).setReadOnly(false);
        verify(c.createStatement()).executeQuery(startsWith("SELECT pg_catalog.current_database(), current_user"));
        verify(c.createStatement(),never()).execute(anyString());verify(c,never()).commit();
    }
    @Test void peerMismatchOrDdlPrivilegesCloseConnectionAndExposeNoDriverDetails() throws Exception {
        for(int i=0;i<8;i++) {
            var c=connection(i==0?"other":"postgres",i==1?"other":"gwon_dev_app",i!=2,i==3,i==4,i==5);
            when(c.createStatement().executeQuery(anyString()).getBoolean(7)).thenReturn(i==6);
            when(c.createStatement().executeQuery(anyString()).getBoolean(8)).thenReturn(i==7);
            var ds=mock(DataSource.class);when(ds.getConnection()).thenReturn(c);
            var guarded=new DevDataSourceGuard.VerifiedDataSource(ds,"postgres","gwon_dev_app",DevEnvironmentGuardTest.REF,"gwon_dev_app","");
            var ex=assertThrows(SQLException.class,guarded::getConnection);assertNull(ex.getCause());verify(c).close();
        }
    }
    @Test void inheritedTempIsAllowedWithNoPermanentDdlPrivileges() throws Exception {
        var c=connection("postgres","gwon_dev_app",true,false,false,false);
        var result=c.createStatement().executeQuery("fixture");
        when(result.getBoolean(9)).thenReturn(true);
        DevDataSourceGuard.verifyPeer(c,"postgres","gwon_dev_app");
        verify(c.createStatement()).executeQuery(contains("current_database(),'TEMP'"));
        verify(result,never()).getBoolean(9);
        verify(c,never()).close();
    }
    @Test void alternateCredentialsCannotBypassTheVerifiedPool() throws Exception {
        var ds=mock(DataSource.class);var guarded=new DevDataSourceGuard.VerifiedDataSource(ds,"postgres","gwon_dev_app",DevEnvironmentGuardTest.REF,"gwon_dev_app","");
        assertThrows(SQLException.class,()->guarded.getConnection("other","synthetic-test-only"));verifyNoInteractions(ds);
    }
    @Test void actualJdbcMetadataCannotPointAtAnotherProject() throws Exception {
        var c=connection("postgres","gwon_dev_app",true,false,false,false);
        when(c.getMetaData().getURL()).thenReturn("jdbc:postgresql://db."+DevEnvironmentGuard.PRODUCTION_REF+".supabase.co/postgres");
        var ds=mock(DataSource.class);when(ds.getConnection()).thenReturn(c);
        var guarded=new DevDataSourceGuard.VerifiedDataSource(ds,"postgres","gwon_dev_app",DevEnvironmentGuardTest.REF,"gwon_dev_app","");
        assertThrows(SQLException.class,guarded::getConnection);verify(c,never()).createStatement();verify(c).close();
    }

    private DevDataSourceGuard.VerifiedDataSource pooler(Connection c) throws Exception {
        String host="aws-0-ap-northeast-1.pooler.supabase.com";
        String url=DevEnvironmentGuardTest.settings().get("spring.datasource.url").toString()
            .replace("db."+DevEnvironmentGuardTest.REF+".supabase.co",host);
        when(c.getMetaData().getURL()).thenReturn(url);
        var ds=mock(DataSource.class);when(ds.getConnection()).thenReturn(c);
        return new DevDataSourceGuard.VerifiedDataSource(ds,"postgres","gwon_dev_app",DevEnvironmentGuardTest.REF,
            "gwon_dev_app."+DevEnvironmentGuardTest.REF,host);
    }

    @Test void verifiedPoolerConnectionAcceptsEitherBackendTlsState() throws Exception {
        for(boolean backendTls:new boolean[]{false,true}) {
            var c=connection("postgres","gwon_dev_app",backendTls,false,false,false);
            assertSame(c,pooler(c).getConnection());verify(c,never()).close();
        }
    }

    @Test void poolerStillRejectsWrongDatabaseRoleAndExcessivePrivileges() throws Exception {
        for(int reason=0;reason<7;reason++) {
            var c=connection(reason==0?"other":"postgres",reason==1?"other":"gwon_dev_app",false,
                reason==2,reason==3,reason==4);
            when(c.createStatement().executeQuery(anyString()).getBoolean(7)).thenReturn(reason==5);
            when(c.createStatement().executeQuery(anyString()).getBoolean(8)).thenReturn(reason==6);
            assertThrows(SQLException.class,pooler(c)::getConnection);verify(c).close();
        }
    }

    @Test void poolerCannotAcceptUnverifiedTlsOrCustomVerifierSettings() throws Exception {
        for(String replacement:new String[]{"disable","allow","prefer","require","verify-ca"}) {
            var c=connection("postgres","gwon_dev_app",false,false,false,false);
            var guarded=pooler(c);
            String url=c.getMetaData().getURL().replace("sslmode=verify-full","sslmode="+replacement);
            when(c.getMetaData().getURL()).thenReturn(url);
            assertThrows(SQLException.class,guarded::getConnection);verify(c,never()).createStatement();verify(c).close();
        }
        for(String option:new String[]{"&sslhostnameverifier=unapproved","&sslfactoryarg=unapproved"}) {
            var c=connection("postgres","gwon_dev_app",false,false,false,false);
            var guarded=pooler(c);String url=c.getMetaData().getURL()+option;
            when(c.getMetaData().getURL()).thenReturn(url);
            assertThrows(SQLException.class,guarded::getConnection);verify(c).close();
        }
    }
}
