/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.ReplicaDBCursor;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.Pair;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * JE implementation of the ChangelogDB.
 */
public class JEChangelogDB implements ChangelogDB
{

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /**
   * This map contains the List of updates received from each LDAP server.
   */
  private final Map<DN, Map<Integer, DbHandler>> sourceDbHandlers =
      new ConcurrentHashMap<DN, Map<Integer, DbHandler>>();
  private ReplicationDbEnv dbEnv;
  private String dbDirName = null;
  private File dbDirectory;

  /** The local replication server. */
  private final ReplicationServer replicationServer;

  /**
   * Builds an instance of this class.
   *
   * @param replicationServer
   *          the local replication server.
   */
  public JEChangelogDB(ReplicationServer replicationServer)
  {
    this.replicationServer = replicationServer;
  }

  private Map<Integer, DbHandler> getDomainMap(DN baseDN)
  {
    final Map<Integer, DbHandler> domainMap = sourceDbHandlers.get(baseDN);
    if (domainMap != null)
    {
      return domainMap;
    }
    return Collections.emptyMap();
  }

  private DbHandler getDbHandler(DN baseDN, int serverId)
  {
    return getDomainMap(baseDN).get(serverId);
  }

  /**
   * Provision resources for the specified serverId in the specified replication
   * domain.
   *
   * @param baseDN
   *          the replication domain where to add the serverId
   * @param serverId
   *          the server Id to add to the replication domain
   * @throws ChangelogException
   *           If a database error happened.
   */
  private void commission(DN baseDN, int serverId, ReplicationServer rs)
      throws ChangelogException
  {
    getOrCreateDbHandler(baseDN, serverId, rs);
  }

  private Pair<DbHandler, Boolean> getOrCreateDbHandler(DN baseDN,
      int serverId, ReplicationServer rs) throws ChangelogException
  {
    synchronized (sourceDbHandlers)
    {
      Map<Integer, DbHandler> domainMap = sourceDbHandlers.get(baseDN);
      if (domainMap == null)
      {
        domainMap = new ConcurrentHashMap<Integer, DbHandler>();
        sourceDbHandlers.put(baseDN, domainMap);
      }

      DbHandler dbHandler = domainMap.get(serverId);
      if (dbHandler == null)
      {
        dbHandler =
            new DbHandler(serverId, baseDN, rs, dbEnv, rs.getQueueSize());
        domainMap.put(serverId, dbHandler);
        return Pair.of(dbHandler, true);
      }
      return Pair.of(dbHandler, false);
    }
  }


  /** {@inheritDoc} */
  @Override
  public void initializeDB()
  {
    try
    {
      dbEnv = new ReplicationDbEnv(getFileForPath(dbDirName).getAbsolutePath(),
          replicationServer);
      initializeChangelogState(dbEnv.readChangelogState());
    }
    catch (ChangelogException e)
    {
      Message message =
          ERR_COULD_NOT_READ_DB.get(this.dbDirectory.getAbsolutePath(), e
              .getLocalizedMessage());
      logError(message);
    }
  }

  private void initializeChangelogState(final ChangelogState changelogState)
      throws ChangelogException
  {
    for (Map.Entry<DN, Long> entry :
      changelogState.getDomainToGenerationId().entrySet())
    {
      replicationServer.getReplicationServerDomain(entry.getKey(), true)
          .initGenerationID(entry.getValue());
    }
    for (Map.Entry<DN, List<Integer>> entry :
      changelogState.getDomainToServerIds().entrySet())
    {
      for (int serverId : entry.getValue())
      {
        commission(entry.getKey(), serverId, replicationServer);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void shutdownDB()
  {
    if (dbEnv != null)
    {
      dbEnv.shutdown();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<Integer> getDomainServerIds(DN baseDN)
  {
    return getDomainMap(baseDN).keySet();
  }

  /** {@inheritDoc} */
  @Override
  public long getCount(DN baseDN, int serverId, CSN from, CSN to)
  {
    DbHandler dbHandler = getDbHandler(baseDN, serverId);
    if (dbHandler != null)
    {
      return dbHandler.getCount(from, to);
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public long getDomainChangesCount(DN baseDN)
  {
    long entryCount = 0;
    for (DbHandler dbHandler : getDomainMap(baseDN).values())
    {
      entryCount += dbHandler.getChangesCount();
    }
    return entryCount;
  }

  /** {@inheritDoc} */
  @Override
  public void shutdownDomain(DN baseDN)
  {
    shutdownDbHandlers(getDomainMap(baseDN));
  }

  private void shutdownDbHandlers(Map<Integer, DbHandler> domainMap)
  {
    synchronized (domainMap)
    {
      for (DbHandler dbHandler : domainMap.values())
      {
        dbHandler.shutdown();
      }
      domainMap.clear();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Map<Integer, CSN> getDomainFirstCSNs(DN baseDN)
  {
    final Map<Integer, DbHandler> domainMap = getDomainMap(baseDN);
    final Map<Integer, CSN> results =
        new HashMap<Integer, CSN>(domainMap.size());
    for (DbHandler dbHandler : domainMap.values())
    {
      results.put(dbHandler.getServerId(), dbHandler.getFirstChange());
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public Map<Integer, CSN> getDomainLastCSNs(DN baseDN)
  {
    final Map<Integer, DbHandler> domainMap = getDomainMap(baseDN);
    final Map<Integer, CSN> results =
        new HashMap<Integer, CSN>(domainMap.size());
    for (DbHandler dbHandler : domainMap.values())
    {
      results.put(dbHandler.getServerId(), dbHandler.getLastChange());
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public void clearDomain(DN baseDN)
  {
    final Map<Integer, DbHandler> domainMap = getDomainMap(baseDN);
    synchronized (domainMap)
    {
      for (DbHandler dbHandler : domainMap.values())
      {
        try
        {
          dbHandler.clear();
        }
        catch (Exception e)
        {
          // TODO: i18n
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_ERROR_CLEARING_DB.get(dbHandler.toString(), e
              .getMessage()
              + " " + stackTraceToSingleLineString(e)));
          logError(mb.toMessage());
        }
      }
      shutdownDbHandlers(domainMap);
    }

    try
    {
      dbEnv.clearGenerationId(baseDN);
    }
    catch (Exception ignored)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.WARNING, ignored);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPurgeDelay(long delay)
  {
    for (Map<Integer, DbHandler> domainMap : sourceDbHandlers.values())
    {
      for (DbHandler dbHandler : domainMap.values())
      {
        dbHandler.setPurgeDelay(delay);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getDomainLatestTrimDate(DN baseDN)
  {
    long latest = 0;
    for (DbHandler dbHandler : getDomainMap(baseDN).values())
    {
      if (latest == 0 || latest < dbHandler.getLatestTrimDate())
      {
        latest = dbHandler.getLatestTrimDate();
      }
    }
    return latest;
  }

  /** {@inheritDoc} */
  @Override
  public CSN getCSNAfter(DN baseDN, int serverId, CSN startAfterCSN)
  {
    final DbHandler dbHandler = getDbHandler(baseDN, serverId);

    ReplicaDBCursor cursor = null;
    try
    {
      cursor = dbHandler.generateCursorFrom(startAfterCSN);
      if (cursor != null && cursor.getChange() != null)
      {
        return cursor.getChange().getCSN();
      }
      return null;
    }
    catch (ChangelogException e)
    {
      // there's no change older than startAfterCSN
      return new CSN(0, 0, serverId);
    }
    finally
    {
      close(cursor);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexDB newChangeNumberIndexDB() throws ChangelogException
  {
    return new DraftCNDbHandler(replicationServer, this.dbEnv);
  }

  /** {@inheritDoc} */
  @Override
  public void setReplicationDBDirectory(String dbDirName)
      throws ConfigException
  {
    if (dbDirName == null)
    {
      dbDirName = "changelogDb";
    }
    this.dbDirName = dbDirName;

    // Check that this path exists or create it.
    dbDirectory = getFileForPath(this.dbDirName);
    try
    {
      if (!dbDirectory.exists())
      {
        dbDirectory.mkdir();
      }
    }
    catch (Exception e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(e.getLocalizedMessage());
      mb.append(" ");
      mb.append(String.valueOf(dbDirectory));
      Message msg = ERR_FILE_CHECK_CREATE_FAILED.get(mb.toString());
      throw new ConfigException(msg, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getDBDirName()
  {
    return this.dbDirName;
  }

  /** {@inheritDoc} */
  @Override
  public ReplicaDBCursor getCursorFrom(DN baseDN, int serverId,
      CSN startAfterCSN)
  {
    DbHandler dbHandler = getDbHandler(baseDN, serverId);
    if (dbHandler == null)
    {
      return null;
    }

    ReplicaDBCursor it;
    try
    {
      it = dbHandler.generateCursorFrom(startAfterCSN);
    }
    catch (Exception e)
    {
      return null;
    }

    if (!it.next())
    {
      close(it);
      return null;
    }

    return it;
  }

  /** {@inheritDoc} */
  @Override
  public boolean publishUpdateMsg(DN baseDN, int serverId,
      UpdateMsg updateMsg) throws ChangelogException
  {
    final Pair<DbHandler, Boolean> pair =
        getOrCreateDbHandler(baseDN, serverId, replicationServer);
    final DbHandler dbHandler = pair.getFirst();
    final boolean wasCreated = pair.getSecond();

    dbHandler.add(updateMsg);
    return wasCreated;
  }

}