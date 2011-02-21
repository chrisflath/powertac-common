/*
 * Copyright 2009-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package org.powertac.common

import grails.test.GrailsUnitTestCase

class TariffTransactionTests extends GrailsUnitTestCase {

  TariffTransaction tx

  protected void setUp() {
    super.setUp()
    tx = new TariffTransaction()
    mockForConstraintsTests(TariffTransaction)
  }

  protected void tearDown() {
    super.tearDown()
  }

  void testNullableValidationLogic() {
    TariffTransaction tx1 = new TariffTransaction(id: null, competition: null)
    assertNull(tx1.id)
    assertFalse(tx1.validate())
    assertEquals('nullable', tx1.errors.getFieldError('id').getCode())
    assertEquals('nullable', tx1.errors.getFieldError('customerInfo').getCode())
    assertEquals('nullable', tx1.errors.getFieldError('tariff').getCode())
    assertEquals('nullable', tx1.errors.getFieldError('timeslot').getCode())
  }

  void testBlankValidationLogic() {
    TariffTransaction meterReading1 = new TariffTransaction(id: '')
    assertFalse(meterReading1.validate())
    assertEquals('blank', meterReading1.errors.getFieldError('id').getCode())
  }
}
