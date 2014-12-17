/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS
 */

package org.opends.server.backends.pluggable.spi;

import java.io.Closeable;
import java.util.Map;

public interface Storage extends Closeable
{
  void initialize(Map<String, String> options) throws Exception;

  Importer startImport() throws Exception;

  void open() throws Exception;

  <T> T read(ReadOperation<T> readTransaction) throws Exception;

  void write(WriteOperation updateTransaction) throws Exception;

  Cursor openCursor(TreeName name);

  @Override
  void close();
}