package dev.abone.provisioning.config;

import com.atomikos.icatch.config.UserTransactionServiceImp;
import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.atomikos.jms.AtomikosConnectionFactoryBean;
import java.sql.SQLException;
import java.util.Properties;
import oracle.jdbc.xa.client.OracleXADataSource;
import org.apache.activemq.artemis.jms.client.ActiveMQXAConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.jta.JtaTransactionManager;

/**
 * Oracle ve Artemis'i ayni dagitik (XA) islemde birlestirir. Siparis satiri ile kuyruga
 * atilan mesaj ya birlikte commit edilir ya da birlikte geri alinir.
 */
@Configuration
public class JtaConfig {

    @Bean(initMethod = "init", destroyMethod = "shutdownWait")
    UserTransactionServiceImp userTransactionService(AltyapiOzellikleri ozellikler) {
        AltyapiOzellikleri.Jta jta = ozellikler.jta();
        Properties p = new Properties();
        p.setProperty("com.atomikos.icatch.tm_unique_name", jta.tmAdi());
        p.setProperty("com.atomikos.icatch.log_base_dir", jta.logDizini());
        p.setProperty("com.atomikos.icatch.default_jta_timeout", String.valueOf(jta.zamanAsimiSn() * 1000L));
        p.setProperty("com.atomikos.icatch.max_timeout", String.valueOf(jta.zamanAsimiSn() * 2000L));
        return new UserTransactionServiceImp(p);
    }

    @Bean(initMethod = "init", destroyMethod = "close")
    @DependsOn("userTransactionService")
    UserTransactionManager atomikosTransactionManager() {
        UserTransactionManager tm = new UserTransactionManager();
        tm.setStartupTransactionService(false);
        tm.setForceShutdown(false);
        return tm;
    }

    @Bean
    JtaTransactionManager transactionManager(UserTransactionManager atomikosTransactionManager) {
        return new JtaTransactionManager(new UserTransactionImp(), atomikosTransactionManager);
    }

    @Bean(initMethod = "init", destroyMethod = "close")
    @DependsOn("userTransactionService")
    AtomikosDataSourceBean dataSource(AltyapiOzellikleri ozellikler) throws SQLException {
        AltyapiOzellikleri.Oracle oracle = ozellikler.oracle();
        OracleXADataSource xa = new OracleXADataSource();
        xa.setURL(oracle.url());
        xa.setUser(oracle.kullanici());
        xa.setPassword(oracle.sifre());

        AtomikosDataSourceBean ds = new AtomikosDataSourceBean();
        ds.setUniqueResourceName("oracle-xa");
        ds.setXaDataSource(xa);
        ds.setMinPoolSize(2);
        ds.setMaxPoolSize(oracle.havuz());
        ds.setBorrowConnectionTimeout(10);
        // Hibernate'in acilistaki sema dogrulamasi gibi JTA disi okumalara izin ver
        ds.setLocalTransactionMode(true);
        return ds;
    }

    @Bean(initMethod = "init", destroyMethod = "close")
    @DependsOn("userTransactionService")
    AtomikosConnectionFactoryBean connectionFactory(AltyapiOzellikleri ozellikler) {
        AltyapiOzellikleri.Artemis artemis = ozellikler.artemis();
        ActiveMQXAConnectionFactory xa =
                new ActiveMQXAConnectionFactory(artemis.url(), artemis.kullanici(), artemis.sifre());

        AtomikosConnectionFactoryBean cf = new AtomikosConnectionFactoryBean();
        cf.setUniqueResourceName("artemis-xa");
        cf.setXaConnectionFactory(xa);
        cf.setMaxPoolSize(artemis.havuz());
        cf.setBorrowConnectionTimeout(10);
        return cf;
    }
}
