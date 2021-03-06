/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer;

import org.forgerock.opendj.ldap.DN;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.util.Utils;

/**
 * This class is used to provide a data model for the Data Replication
 * Options panel of the installer.
 */
public class DataReplicationOptions
{
  /**
   * This enumeration is used to know what the user wants to do for the data
   * (import data or not, what use as source of the data...).
   */
  public enum Type
  {
    /** Standalone server. */
    STANDALONE,
    /** Replicate Contents and this is the first server in topology.. */
    FIRST_IN_TOPOLOGY,
    /** Replicate Contents of the new Suffix with existing server. */
    IN_EXISTING_TOPOLOGY
  }

  private Type type;
  private int replicationPort = getDefaultReplicationPort();
  private boolean secureReplication;
  private AuthenticationData authenticationData = new AuthenticationData();
  {
    authenticationData.setDn(DN.valueOf(Constants.DIRECTORY_MANAGER_DN));
    authenticationData.setPort(4444);
  }

  /** Private constructor for the DataReplicationOptions object. */
  private DataReplicationOptions()
  {
  }

  /**
   * Construct an FIRST_IN_TOPOLOGY object.
   * @param replicationPort the replication port.
   * @param secureReplication whether servers must encrypt data for the
   * replication communication with this server.
   * @return the FIRST_IN_TOPOLOGY object.
   */
  public static DataReplicationOptions createFirstInTopology(
      int replicationPort, boolean secureReplication)
  {
    DataReplicationOptions options = new DataReplicationOptions();
    options.type = Type.FIRST_IN_TOPOLOGY;
    options.replicationPort = replicationPort;
    options.secureReplication = secureReplication;
    return options;
  }

  /**
   * Construct an STANDALONE object.
   * @return the STANDALONE object.
   */
  public static DataReplicationOptions createStandalone()
  {
    DataReplicationOptions options = new DataReplicationOptions();
    options.type = Type.STANDALONE;
    return options;
  }

  /**
   * Construct an IN_EXISTING_TOPOLOGY object.
   * @param authenticationData the authentication data.
   * @param replicationPort the replication port.
   * @param secureReplication whether servers must encrypt data for the
   * replication communication with this server.
   * @return the IN_EXISTING_TOPOLOGY object.
   */
  public static DataReplicationOptions createInExistingTopology(
      AuthenticationData authenticationData, int replicationPort,
      boolean secureReplication)
  {
    DataReplicationOptions options = new DataReplicationOptions();
    options.type = Type.IN_EXISTING_TOPOLOGY;
    options.authenticationData = authenticationData;
    options.replicationPort = replicationPort;
    options.secureReplication = secureReplication;
    return options;
  }

  /**
   * Returns the type of DataReplicationOptions represented by this object
   * (replicate or not).
   *
   * @return the type of DataReplicationOptions.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the AuthenticationData to the server used to replicate.
   * If it is standalone returns null.
   *
   * @return the AuthenticationData to the server used to replicate.
   */
  public AuthenticationData getAuthenticationData()
  {
    return authenticationData;
  }

  /**
   * Returns the port that is going to be used for replication.
   *
   * @return the replication that must be used to configure replication.
   */
  public int getReplicationPort()
  {
    return replicationPort;
  }

  /**
   * Returns whether servers must encrypt data for the replication communication
   * with this server.
   *
   * @return <CODE>true</CODE> if the servers must encrypt data for the
   * replication communication and <CODE>false</CODE> otherwise.
   */
  public boolean useSecureReplication()
  {
    return secureReplication;
  }

  /**
   * Provides the port that will be proposed to the user in the replication
   * options panel of the installation wizard. It will check whether we can use
   * ports of type X989 and if not it will return -1.
   *
   * @return the free port of type X989 if it is available and we can use and -1
   * if not.
   */
  private static int getDefaultReplicationPort()
  {
    int defaultPort = -1;

    for (int i=0;i<10000 && defaultPort == -1;i+=1000)
    {
      int port = i + Constants.DEFAULT_REPLICATION_PORT;
      if (Utils.canUseAsPort(port))
      {
        defaultPort = port;
      }
    }
    return defaultPort;
  }
}

