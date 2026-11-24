package io.ddd4j.sample.micronaut.cqrs;

import io.ddd4j.data.event.store.jpa.StoredEventEntity;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.postgresql.ds.PGSimpleDataSource;

/** 为独立进程回归准备专用 PostgreSQL 夹具。 */
public final class S04DatabaseFixture {
    private S04DatabaseFixture() {
    }

    /** @param args 隔离数据库的 JDBC URL、用户和密码 */
    public static void main(String[] args) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(args[0]);
        source.setUser(args[1]);
        source.setPassword(args[2]);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();

        Configuration configuration = new Configuration()
                .addAnnotatedClass(StoredEventEntity.class)
                .addAnnotatedClass(ProjectionPositionEntity.class);
        configuration.getProperties().put("hibernate.connection.datasource", source);
        configuration.setProperty("hibernate.hbm2ddl.auto", "create");
        try (SessionFactory factory = configuration.buildSessionFactory()) {
            if (!factory.isOpen()) {
                throw new IllegalStateException("fixture factory did not start");
            }
        }
    }
}
