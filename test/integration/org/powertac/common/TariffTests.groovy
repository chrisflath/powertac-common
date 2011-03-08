/*
 * Copyright (c) 2011 by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powertac.common

import grails.test.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.joda.time.Instant
import org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent

class TariffTests extends GrailsUnitTestCase 
{
  def timeService // dependency injection
  
  TariffSpecification tariffSpec // instance var

  Instant start
  Instant exp
  Broker broker
  
  protected void setUp () 
  {
    super.setUp()
    AuditLogEvent.list()*.delete()
    start = new DateTime(2011, 1, 1, 12, 0, 0, 0, DateTimeZone.UTC).toInstant()
    timeService.setCurrentTime(start)
    broker = new Broker (userName: 'testBroker')
    assert broker.save()
    exp = new DateTime(2011, 3, 1, 12, 0, 0, 0, DateTimeZone.UTC).toInstant()
    tariffSpec = new TariffSpecification(brokerId: broker.id, expiration: exp,
                                         minDuration: TimeService.WEEK * 8)
  }

  protected void tearDown ()
  {
    super.tearDown()
  }

  // create a Tariff and inspect it
  void testCreate () 
  {
    Rate r1 = new Rate(value: 0.121)
    tariffSpec.addToRates(r1)
    assert tariffSpec.save()
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    assertNotNull("non-null result", te)
    assertEquals("correct TariffSpec", tariffSpec, te.tariffSpec)
    assertEquals("correct initial realized price", 0.0, te.realizedPrice)
    assertEquals("correct expiration in spec", exp, te.tariffSpec.getExpiration())
    assertEquals("correct expiration", exp, te.getExpiration())
    assertEquals("correct publication time", start, te.offerDate)
    assertFalse("not expired", te.isExpired())
    assertTrue("covered", te.isCovered())
  }
  
  // check the realized price calculation
  void testRealizedPrice ()
  {
    Rate r1 = new Rate(value: 0.121)
    tariffSpec.addToRates(r1)
    assert tariffSpec.save()
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    te.setTotalUsage 501.2
    te.setTotalCost 99.8
    assertEquals("Correct realized price", 99.8/501.2, te.getRealizedPrice(), 1.0e-6)
  }

  // single fixed rate, check charges in past and future  
  void testSimpleRate ()
  {
    Rate r1 = new Rate(value: 0.121)
    tariffSpec.addToRates(r1)
    assert tariffSpec.save()
    Instant now = timeService.currentTime
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    assertEquals("correct charge, default case", 0.121, te.getUsageCharge())
    assertEquals("correct charge, today", 1.21, te.getUsageCharge(10.0))
    assertEquals("correct charge yesterday", 2.42, te.getUsageCharge(now - TimeService.DAY, 20.0))
    assertEquals("correct charge tomorrow", 12.1, te.getUsageCharge(now + TimeService.DAY, 100.0))
    assertEquals("correct charge an hour ago", 3.63, te.getUsageCharge(now - TimeService.HOUR, 30.0))
    assertEquals("correct charge an hour from now", 1.21, te.getUsageCharge(now + TimeService.HOUR, 10.0))
    assertEquals("daily rate map", 1, te.rateMap.size())
    assertEquals("rate map has 24 entries", 24, te.rateMap[0].size())
    assertTrue("covered", te.isCovered())
  }
  
  // single fixed rate, check realized price after multiple rounds
  void testSimpleRateRealizedPrice ()
  {
    Rate r1 = new Rate(value: 0.131)
    tariffSpec.addToRates(r1)
    assert tariffSpec.save()
    Instant now = timeService.currentTime
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    te.getUsageCharge(20.0, 200.0, true)
    assertEquals("realized price 1", 0.131, te.realizedPrice)
    te.getUsageCharge(10.0, 1000.0, true)
    assertEquals("realized price 2", 0.131, te.realizedPrice)
    te.getUsageCharge(3.0, 20.0, true)
    assertEquals("realized price 3", 0.131, te.realizedPrice)
  }
  
  // time-of-use rates: 0.15/kwh 7:00-18:00, 0.08/kwh 18:00-7:00
  void testTimeOfUseDaily ()
  {
    Rate r1 = new Rate(value: 0.15, dailyBegin: 7, dailyEnd: 17)
    assert r1.save()
    Rate r2 = new Rate(value: 0.08, dailyBegin: 18, dailyEnd: 6)
    assert r2.save()
    tariffSpec.addToRates(r1)
    tariffSpec.addToRates(r2)
    assert tariffSpec.save()
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    assertEquals("noon price", 3.0, te.getUsageCharge(20.0, 200.0, true))
    assertEquals("realized price", 0.15, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 1, 18, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("18:00 price", 0.8, te.getUsageCharge(10.0, 220.0, true))
    assertEquals("realized price 2", 3.8/30.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 2, 0, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("midnight price", 0.4, te.getUsageCharge(5.0, 230.0, true))
    assertEquals("realized price 3", 4.2/35.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 2, 7, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("7:00 price", 0.6, te.getUsageCharge(4.0, 235.0, true))
    assertEquals("realized price 4", 4.8/39.0, te.realizedPrice, 1e-6)
    assertTrue("covered", te.isCovered())

    println "Audit record count: ${AuditLogEvent.count()}"
    //AuditLogEvent.list().each { println it.toString() }
    def trace = AuditLogEvent.findAllByClassNameAndPersistedObjectId(te.getClass().getName(), te.id)
    trace.each { println "Log ${it.className} ${it.persistedObjectId}, prop:${it.propertyName} was ${it.oldValue}, now ${it.newValue}" }
  }
  
  // time-of-use rates: 0.15/kwh 7:00-18:00, 0.08/kwh 19:00-7:00
  void testTimeOfUseDailyGap ()
  {
    Rate r1 = new Rate(value: 0.15, dailyBegin: 7, dailyEnd: 17)
    assert r1.save()
    Rate r2 = new Rate(value: 0.08, dailyBegin: 19, dailyEnd: 6)
    assert r2.save()
    tariffSpec.addToRates(r1)
    tariffSpec.addToRates(r2)
    assert tariffSpec.save()
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    assertFalse("not covered", te.isCovered())
  }

  // time-of-use weekly: 
  // - weekdays are 0.15/kwh 7:00-18:00, 0.08/kwh 18:00-7:00
  // - weekends are 0.06
  void testTimeOfUseWeekly ()
  {
    Rate r1 = new Rate(value: 0.15, dailyBegin: 7, dailyEnd: 17)
    Rate r2 = new Rate(value: 0.08, dailyBegin: 18, dailyEnd: 6)
    Rate r3 = new Rate(value: 0.06, weeklyBegin: 6, weeklyEnd: 7)
    tariffSpec.addToRates(r1)
    tariffSpec.addToRates(r2)
    tariffSpec.addToRates(r3)
    assert tariffSpec.save()
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    assertEquals("noon price Sat", 1.2, te.getUsageCharge(20.0, 200.0, true))
    assertEquals("realized price", 0.06, te.realizedPrice, 1e-6)
    assertTrue("weekly map", te.isWeekly)
    assertEquals("rate map row has 168 entries", 168, te.rateMap[0].size())
    assertTrue("covered", te.isCovered())
    timeService.currentTime = new DateTime(2011, 1, 1, 18, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("18:00 price Sat", 0.6, te.getUsageCharge(10.0, 220.0, true))
    assertEquals("realized price 2", 1.8/30.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 2, 0, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("midnight price Sun", 0.3, te.getUsageCharge(5.0, 230.0, true))
    assertEquals("realized price 3", 2.1/35.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 2, 7, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("7:00 price Sun", 0.24, te.getUsageCharge(4.0, 235.0, true))
    assertEquals("realized price 4", 2.34/39.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 3, 0, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("midnight Mon", 0.32, te.getUsageCharge(4.0, 235.0, true))
    assertEquals("realized price 5", 2.66/43.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 3, 6, 59, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("6:59 Mon", 0.48, te.getUsageCharge(6.0, 235.0, true))
    assertEquals("realized price 6", 3.14/49.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 3, 7, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("7:00 Mon", 1.2, te.getUsageCharge(8.0, 235.0, true))
    assertEquals("realized price 7", 4.34/57.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 4, 12, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("noon Tue", 1.5, te.getUsageCharge(10.0, 235.0, true))
    assertEquals("realized price 8", 5.84/67.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 5, 17, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("17:00 Wed", 1.05, te.getUsageCharge(7.0, 235.0, true))
    assertEquals("realized price 9", 6.89/74.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 6, 18, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("18:00 Thu", 0.72, te.getUsageCharge(9.0, 235.0, true))
    assertEquals("realized price 10", 7.61/83.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 7, 23, 59, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("23:59 Fri", 0.96, te.getUsageCharge(12.0, 235.0, true))
    assertEquals("realized price 11", 8.57/95.0, te.realizedPrice, 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 8, 12, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("midnight Sat", 0.18, te.getUsageCharge(3.0, 235.0, true))
    assertEquals("realized price 12", 8.75/98.0, te.realizedPrice, 1e-6)
  }

  // time-of-use weekly wrap-around
  void testTimeOfUseWeeklyWrap ()
  {
    Rate r1 = new Rate(value: 0.15, dailyBegin: 6, dailyEnd: 17)
    Rate r2 = new Rate(value: 0.08, dailyBegin: 18, dailyEnd: 5)
    Rate r3 = new Rate(value: 0.06, weeklyBegin: 7, weeklyEnd: 2) // Sun-Tue
    tariffSpec.addToRates(r1)
    tariffSpec.addToRates(r2)
    tariffSpec.addToRates(r3)
    assert tariffSpec.save()
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    timeService.currentTime = new DateTime(2011, 1, 1, 23, 50, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("23:50 Sat", 0.8, te.getUsageCharge(10.0, 220.0, true))
    timeService.currentTime = new DateTime(2011, 1, 2, 0, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("midnight Sun", 0.3, te.getUsageCharge(5.0, 230.0, true))
    timeService.currentTime = new DateTime(2011, 1, 3, 7, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("7:00 price Mon", 0.24, te.getUsageCharge(4.0, 235.0, true))
    timeService.currentTime = new DateTime(2011, 1, 3, 20, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("20:00 Mon", 0.48, te.getUsageCharge(8.0, 235.0, true))
    timeService.currentTime = new DateTime(2011, 1, 4, 1, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("1:00 Tue", 0.12, te.getUsageCharge(2.0, 235.0, true))
    timeService.currentTime = new DateTime(2011, 1, 4, 12, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("noon Tue", 0.3, te.getUsageCharge(5.0, 235.0, true))
    timeService.currentTime = new DateTime(2011, 1, 4, 23, 59, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("23:56 Tue", 3.0, te.getUsageCharge(50.0, 235.0, true))
    timeService.currentTime = new DateTime(2011, 1, 5, 0, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("midnight Wed", 0.64, te.getUsageCharge(8.0, 235.0, true))
    timeService.currentTime = new DateTime(2011, 1, 5, 12, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("noon Wed", 2.25, te.getUsageCharge(15.0, 235.0, true))
  }
  
  // tiers
  void testTimeOfUseTier ()
  {
    Rate r1 = new Rate(value: 0.15, dailyBegin: 7, dailyEnd: 17)
    Rate r2 = new Rate(value: 0.08, dailyBegin: 18, dailyEnd: 6)
    Rate r3 = new Rate(value: 0.2, tierThreshold: 20)
    tariffSpec.addToRates(r1)
    tariffSpec.addToRates(r2)
    tariffSpec.addToRates(r3)
    assert tariffSpec.save()
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    assertEquals("noon price, below", 1.5, te.getUsageCharge(10.0, 5.0, true), 1e-6)
    assertEquals("noon price, above", 2.0, te.getUsageCharge(10.0, 25.0, true), 1e-6)
    assertEquals("noon price, split", 1.75, te.getUsageCharge(10.0, 15.0, true), 1e-6)
    timeService.currentTime = new DateTime(2011, 1, 2, 0, 0, 0, 0, DateTimeZone.UTC).toInstant()
    assertEquals("midnight price, below", 0.4, te.getUsageCharge(5.0, 12.0, true), 1e-6)
    assertEquals("midnight price, above", 1.0, te.getUsageCharge(5.0, 22.0, true), 1e-6)
    assertEquals("midnight price, split", 0.76, te.getUsageCharge(5.0, 18.0, true), 1e-6)
  }
  
  // multiple tiers
  void testMultiTiers ()
  {
    Rate r1 = new Rate(value: 0.15, tierThreshold: 10)
    Rate r2 = new Rate(value: 0.1, tierThreshold: 5)
    Rate r3 = new Rate(value: 0.2, tierThreshold: 20)
    Rate r4 = new Rate(value: 0.07)
    tariffSpec.addToRates(r1)
    tariffSpec.addToRates(r2)
    tariffSpec.addToRates(r3)
    tariffSpec.addToRates(r4)
    assert tariffSpec.save()
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    assertEquals("first tier", 0.14, te.getUsageCharge(2.0, 2.0, true), 1e-6)
    assertEquals("first-second tier", 0.41, te.getUsageCharge(5.0, 2.0, true), 1e-6)
    assertEquals("second tier", 0.2, te.getUsageCharge(2.0, 6.0, true), 1e-6)
    assertEquals("second-third tier", 0.6, te.getUsageCharge(5.0, 7.0, true), 1e-6)
    assertEquals("third tier", 0.3, te.getUsageCharge(2.0, 12.0, true), 1e-6)
    assertEquals("third-fourth tier", 0.85, te.getUsageCharge(5.0, 17.0, true), 1e-6)
    assertEquals("fourth tier", 0.4, te.getUsageCharge(2.0, 22.0, true), 1e-6)
    assertEquals("second-fourth tier", 2.1, te.getUsageCharge(14.0, 8.0, true), 1e-6)
  }

  // variable
  void testVarRate ()
  {
    Rate r1 = new Rate(isFixed: false, minValue: 0.05, maxValue: 0.50,
                       noticeInterval: 3, expectedMean: 0.10, dailyBegin: 7, dailyEnd: 17)
    Rate r2 = new Rate(value: 0.08, dailyBegin: 18, dailyEnd: 6)
    tariffSpec.addToRates(r1)
    tariffSpec.addToRates(r2)
    assert tariffSpec.save()
    r1.addToRateHistory(new HourlyCharge(value: 0.09, atTime: new DateTime(2011, 1, 1, 12, 0, 0, 0, DateTimeZone.UTC).toInstant()))
    r1.addToRateHistory(new HourlyCharge(value: 0.11, atTime: new DateTime(2011, 1, 1, 13, 0, 0, 0, DateTimeZone.UTC).toInstant()))
    r1.addToRateHistory(new HourlyCharge(value: 0.13, atTime: new DateTime(2011, 1, 1, 14, 0, 0, 0, DateTimeZone.UTC).toInstant()))
    r1.addToRateHistory(new HourlyCharge(value: 0.14, atTime: new DateTime(2011, 1, 1, 15, 0, 0, 0, DateTimeZone.UTC).toInstant()))
    Tariff te = new Tariff(tariffSpec: tariffSpec)
    te.init()
    assert te.save()
    assertEquals("current charge, noon Sunday", 0.9, te.getUsageCharge(10.0), 1e-6)
    assertEquals("13:00 charge, noon Sunday", 1.1,
      te.getUsageCharge(new DateTime(2011, 1, 1, 13, 0, 0, 0, DateTimeZone.UTC).toInstant(), 10.0), 1e-6)
    assertEquals("14:00 charge, noon Sunday", 1.3,
      te.getUsageCharge(new DateTime(2011, 1, 1, 14, 0, 0, 0, DateTimeZone.UTC).toInstant(), 10.0), 1e-6)
    assertEquals("15:00 charge, noon Sunday", 1.4,
      te.getUsageCharge(new DateTime(2011, 1, 1, 15, 0, 0, 0, DateTimeZone.UTC).toInstant(), 10.0), 1e-6)
    assertEquals("16:00 charge, noon Sunday", 1.0,
      te.getUsageCharge(new DateTime(2011, 1, 1, 16, 0, 0, 0, DateTimeZone.UTC).toInstant(), 10.0), 1e-6)
    assertEquals("18:00 charge, noon Sunday", 0.8,
      te.getUsageCharge(new DateTime(2011, 1, 1, 18, 0, 0, 0, DateTimeZone.UTC).toInstant(), 10.0), 1e-6)
  }
}
