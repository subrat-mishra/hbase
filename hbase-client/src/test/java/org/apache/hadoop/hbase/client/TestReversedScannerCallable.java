/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.client;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.RegionLocations;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotEnabledException;
import org.apache.hadoop.hbase.ipc.RpcControllerFactory;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Category({ ClientTests.class, SmallTests.class })
public class TestReversedScannerCallable {

  private static final TableName TABLE_NAME = TableName.valueOf("TestReversedScannerCallable");

  private static final String HOSTNAME = "localhost";
  private static final ServerName SERVERNAME = ServerName.valueOf(HOSTNAME, 60030, 123);
  private static final byte[] ROW = Bytes.toBytes("row1");
  private static final Scan DEFAULT_SCAN = new Scan().withStartRow(ROW, true).setReversed(true);

  @Mock
  private ClusterConnection connection;
  @Mock
  private RpcControllerFactory rpcFactory;
  @Mock
  private RegionLocations regionLocations;
  @Mock
  private HRegionLocation regionLocation;

  @Before
  public void setUp() throws Exception {
    when(connection.getConfiguration()).thenReturn(new Configuration());
    when(regionLocations.size()).thenReturn(1);
    when(regionLocations.getRegionLocation(0)).thenReturn(regionLocation);
    when(regionLocation.getHostname()).thenReturn(HOSTNAME);
    when(regionLocation.getServerName()).thenReturn(SERVERNAME);
  }

  @Test
  public void testPrepareAlwaysUsesCache() throws Exception {
    when(connection.locateRegion(TABLE_NAME, ROW, true, true, 0))
      .thenReturn(regionLocations);

    ReversedScannerCallable callable =
      new ReversedScannerCallable(connection, TABLE_NAME, DEFAULT_SCAN, null, rpcFactory, 0);
    callable.prepare(false);
    callable.prepare(true);

    verify(connection, times(2)).locateRegion(TABLE_NAME, ROW, true, true, 0);
  }

  @Test
  public void testHandleDisabledTable() throws IOException {
    when(connection.isTableDisabled(TABLE_NAME)).thenReturn(true);

    ReversedScannerCallable callable =
        new ReversedScannerCallable(connection, TABLE_NAME, DEFAULT_SCAN, null, rpcFactory, 0);

    try {
      callable.prepare(true);
      fail("should have thrown TableNotEnabledException");
    } catch (TableNotEnabledException e) {
      // pass
    }
  }

  @Test
  public void testUpdateSearchKeyCacheLocation() throws IOException {
    byte[] regionName = HRegionInfo.createRegionName(TABLE_NAME,
        ConnectionUtils.createCloseRowBefore(ConnectionUtils.MAX_BYTE_ARRAY), "123", false);
    HRegionInfo mockRegionInfo = mock(HRegionInfo.class);
    when(mockRegionInfo.containsRow(ConnectionUtils.MAX_BYTE_ARRAY)).thenReturn(true);
    when(mockRegionInfo.getEndKey()).thenReturn(HConstants.EMPTY_END_ROW);
    when(mockRegionInfo.getRegionName()).thenReturn(regionName);
    when(regionLocation.getRegionInfo()).thenReturn(mockRegionInfo);

    IOException testThrowable = new IOException("test throwable");

    when(connection.locateRegion(TABLE_NAME, ConnectionUtils.MAX_BYTE_ARRAY, true, true, 0))
        .thenReturn(regionLocations);

    Scan scan = new Scan().setReversed(true);
    ReversedScannerCallable callable =
        new ReversedScannerCallable(connection, TABLE_NAME, scan, null, rpcFactory, 0);

    callable.prepare(false);

    callable.throwable(testThrowable, true);

    verify(connection).updateCachedLocations(TABLE_NAME, regionName,
      ConnectionUtils.MAX_BYTE_ARRAY, testThrowable, SERVERNAME);
  }
}
