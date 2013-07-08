package org.wikapidia.core.dao.sql;

import com.typesafe.config.Config;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;
import org.wikapidia.conf.Configuration;
import org.wikapidia.conf.ConfigurationException;
import org.wikapidia.conf.Configurator;
import org.wikapidia.core.dao.DaoException;
import org.wikapidia.core.dao.DaoFilter;
import org.wikapidia.core.dao.LocalLinkDao;
import org.wikapidia.core.jooq.Tables;
import org.wikapidia.core.lang.Language;
import org.wikapidia.core.model.LocalLink;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;


public class LocalLinkSqlDao extends AbstractSqlDao implements LocalLinkDao {
    private File tmpFile = null;
    private CsvListWriter writer = null;


    public LocalLinkSqlDao (DataSource dataSource) throws DaoException {
        super(dataSource);
    }

    @Override
    public void clear() throws DaoException {
        executeSqlResource("/db/local-link-drop.sql");
        executeSqlResource("/db/local-link-schema.sql");
    }

    @Override
    public void beginLoad() throws DaoException {
        if (!dialect.equals(SQLDialect.H2)) {
            throw new IllegalArgumentException("invalid dialect: " + dialect);
        }
        executeSqlResource("/db/local-link-schema.sql");
        try {
            tmpFile = File.createTempFile("locallink", "db");
            tmpFile.deleteOnExit();
            writer = new CsvListWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    new FileOutputStream(tmpFile), "UTF-8")),
                    CsvPreference.STANDARD_PREFERENCE);
            writer.writeHeader(
                    Tables.LOCAL_LINK.LANG_ID.getName(),
                    Tables.LOCAL_LINK.ANCHOR_TEXT.getName(),
                    Tables.LOCAL_LINK.SOURCE_ID.getName(),
                    Tables.LOCAL_LINK.DEST_ID.getName(),
                    Tables.LOCAL_LINK.LOCATION.getName(),
                    Tables.LOCAL_LINK.IS_PARSEABLE.getName(),
                    Tables.LOCAL_LINK.LOCATION_TYPE.getName()
            );
        } catch (IOException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public void save(LocalLink localLink) throws DaoException {
        try {
            synchronized (writer) {
                writer.write(
                        localLink.getLanguage().getId(),
                        localLink.getAnchorText(),
                        localLink.getSourceId(),
                        localLink.getDestId(),
                        localLink.getLocation(),
                        localLink.isParseable(),
                        localLink.getLocType().ordinal()
                );
            }
        } catch (IOException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public void endLoad() throws DaoException {
        if (!dialect.equals(SQLDialect.H2)) {
            throw new IllegalArgumentException("invalid dialect: " + dialect);
        }
        Connection conn = null;
        try {
            writer.close();
            conn = ds.getConnection();
            String quotedPath = tmpFile.getAbsolutePath().replace("'", "''");
            Statement s = conn.createStatement();
            s.execute("INSERT INTO " + Tables.LOCAL_LINK.getName() +
                    " SELECT * " +
                    " FROM CSVREAD('" + quotedPath + "', null, 'charset=UTF-8')");
        } catch (SQLException e) {
            throw new DaoException(e);
        } catch (IOException e) {
            throw new DaoException(e);
        } finally {
            quietlyCloseConn(conn);
        }

        executeSqlResource("/db/local-link-indexes.sql");
    }

    @Override
    public Iterable<LocalLink> get(DaoFilter daoFilter) throws DaoException {
        Connection conn = null;
        try {
            conn = ds.getConnection();
            DSLContext context = DSL.using(conn, dialect);
            Collection<Condition> conditions = new ArrayList<Condition>();
            if (daoFilter.getLangIds() != null) {
                conditions.add(Tables.LOCAL_LINK.LANG_ID.in(daoFilter.getLangIds()));
            }
            if (daoFilter.getLocTypes() != null) {
                conditions.add(Tables.LOCAL_LINK.LOCATION_TYPE.in(daoFilter.getLocTypes()));
            }
            if (daoFilter.getSourceIds() != null) {
                conditions.add(Tables.LOCAL_LINK.SOURCE_ID.in(daoFilter.getSourceIds()));
            }
            if (daoFilter.getDestIds() != null) {
                conditions.add(Tables.LOCAL_LINK.DEST_ID.in(daoFilter.getDestIds()));
            }
            if (daoFilter.isParseable() != null) {
                conditions.add(Tables.LOCAL_LINK.IS_PARSEABLE.in(daoFilter.isParseable()));
            }
//            if (conditions.isEmpty()) {
//                return null;
//            }
            Cursor<Record> result = context.select().
                    from(Tables.LOCAL_LINK).
                    where(conditions).
                    fetchLazy(getFetchSize());
            return new SqlDaoIterable<LocalLink>(result) {
                @Override
                public LocalLink transform(Record r) {
                    return buildLocalLink(r, true);
                }
            };
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            quietlyCloseConn(conn);
        }
    }

    @Override
    public LocalLink getLink(Language language, int sourceId, int destId) throws DaoException {
        Connection conn = null;
        try {
            conn = ds.getConnection();
            DSLContext context = DSL.using(conn, dialect);
            Record record = context.select().
                    from(Tables.LOCAL_LINK).
                    where(Tables.LOCAL_LINK.LANG_ID.eq(language.getId())).
                    and(Tables.LOCAL_LINK.SOURCE_ID.eq(sourceId)).
                    and(Tables.LOCAL_LINK.DEST_ID.eq(destId)).
                    fetchOne();
            return buildLocalLink(record, true);
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            quietlyCloseConn(conn);
        }
    }

    @Override
    public SqlDaoIterable<LocalLink> getLinks(Language language, int localId, boolean outlinks, boolean isParseable, LocalLink.LocationType locationType) throws DaoException{
        Connection conn = null;
        try {
            conn = ds.getConnection();
            DSLContext context = DSL.using(conn, dialect);
            TableField idField;
            if (outlinks){
                idField = Tables.LOCAL_LINK.SOURCE_ID;
            } else {
                idField = Tables.LOCAL_LINK.DEST_ID;
            }
            Cursor<Record> result = context.select()
                    .from(Tables.LOCAL_LINK)
                    .where(Tables.LOCAL_LINK.LANG_ID.equal(language.getId()))
                    .and(idField.equal(localId))
                    .and(Tables.LOCAL_LINK.IS_PARSEABLE.equal(isParseable))
                    .and(Tables.LOCAL_LINK.LOCATION_TYPE.equal((short) locationType.ordinal()))
                    .fetchLazy(getFetchSize());
            return buildLocalLinks(result, outlinks);
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            quietlyCloseConn(conn);
        }
    }

    @Override
    public SqlDaoIterable<LocalLink> getLinks(Language language, int localId, boolean outlinks) throws DaoException{
        Connection conn = null;
        try {
            conn = ds.getConnection();
            DSLContext context = DSL.using(conn, dialect);
            TableField idField;
            if (outlinks){
                idField = Tables.LOCAL_LINK.SOURCE_ID;
            } else {
                idField = Tables.LOCAL_LINK.DEST_ID;
            }
            Cursor<Record> result = context.select()
                    .from(Tables.LOCAL_LINK)
                    .where(Tables.LOCAL_LINK.LANG_ID.equal(language.getId()))
                    .and(idField.equal(localId))
                    .fetchLazy(getFetchSize());
            return buildLocalLinks(result, outlinks);
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            quietlyCloseConn(conn);
        }
    }

    @Override
    public int getNumLinks(Language language, boolean isParseable, LocalLink.LocationType locationType) throws DaoException{
        Connection conn = null;
        try {
            conn = ds.getConnection();
            DSLContext context = DSL.using(conn, dialect);
            Cursor<Record> result = context.select()
                    .from(Tables.LOCAL_LINK)
                    .where(Tables.LOCAL_LINK.LANG_ID.equal(language.getId()))
                    .and(Tables.LOCAL_LINK.IS_PARSEABLE.equal(isParseable))
                    .and(Tables.LOCAL_LINK.LOCATION_TYPE.equal((short)locationType.ordinal()))
                    .fetchLazy(getFetchSize());
            int i = 0;
            for (Record r : result){
                i++;
            }
            return i;
        } catch (SQLException e){
            throw new DaoException(e);
        } finally {
            quietlyCloseConn(conn);
        }
    }

    private SqlDaoIterable<LocalLink> buildLocalLinks(Cursor<Record> result, boolean outlink){
        final boolean o = outlink;
        return new SqlDaoIterable<LocalLink>(result) {
            @Override
            public LocalLink transform(Record r) {
                return buildLocalLink(r, o);
            }
        };
    }

    private LocalLink buildLocalLink(Record record, boolean outlink){
        if (record == null){
            return null;
        }
        return new LocalLink(
                Language.getById(record.getValue(Tables.LOCAL_LINK.LANG_ID)),
                record.getValue(Tables.LOCAL_LINK.ANCHOR_TEXT),
                record.getValue(Tables.LOCAL_LINK.SOURCE_ID),
                record.getValue(Tables.LOCAL_LINK.DEST_ID),
                outlink,
                record.getValue(Tables.LOCAL_LINK.LOCATION),
                record.getValue(Tables.LOCAL_LINK.IS_PARSEABLE),
                LocalLink.LocationType.values()[record.getValue(Tables.LOCAL_LINK.LOCATION_TYPE)]
        );
    }

    public static class Provider extends org.wikapidia.conf.Provider<LocalLinkDao> {
        public Provider(Configurator configurator, Configuration config) throws ConfigurationException {
            super(configurator, config);
        }

        @Override
        public Class<LocalLinkDao> getType() {
            return LocalLinkDao.class;
        }

        @Override
        public String getPath() {
            return "dao.localLink";
        }

        @Override
        public LocalLinkSqlDao get(String name, Config config) throws ConfigurationException {
            if (!config.getString("type").equals("sql")) {
                return null;
            }
            try {
                return new LocalLinkSqlDao(
                        getConfigurator().get(
                                DataSource.class,
                                config.getString("dataSource"))
                );
            } catch (DaoException e) {
                throw new ConfigurationException(e);
            }

        }
    }
}
