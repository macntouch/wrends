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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import static org.opends.server.loggers.Debug.debugConstructor;
import static org.opends.server.loggers.Debug.debugEnter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;



/**
 * This class defines a data structure for a change record entry for
 * an add operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
public final class AddChangeRecordEntry extends ChangeRecordEntry
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
      "org.opends.server.util.AddChangeRecordEntry";


  /**
   * The entry attributes for this operation.
   */
  private final List<Attribute> attributes;



  /**
   * Creates a new entry with the provided information.
   *
   * @param dn
   *          The distinguished name for this entry.
   * @param attributes
   *          The entry attributes for this operation.
   */
  public AddChangeRecordEntry(DN dn,
      Map<AttributeType,List<Attribute>> attributes)
  {
    super(dn);

    assert debugConstructor(CLASS_NAME, String.valueOf(dn),
                            String.valueOf(attributes));


    this.attributes = new ArrayList<Attribute>(attributes.size());
    for (List<Attribute> list : attributes.values())
    {
      for (Attribute a : list)
      {
        this.attributes.add(a);
      }
    }
  }



  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public ChangeOperationType getChangeOperationType()
  {
    assert debugEnter(CLASS_NAME, "getChangeOperationType");

    return ChangeOperationType.ADD;
  }



  /**
   * Retrieves the entire set of attributes for this entry.
   * <p>
   * The returned list is read-only.
   *
   * @return The entire unmodifiable list of attributes for this entry.
   */
  public List<Attribute> getAttributes()
  {
    assert debugEnter(CLASS_NAME, "getAttributes");

    return Collections.unmodifiableList(attributes);
  }

}

