/*
 * Copyright 2011 the original author or authors.
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

import com.thoughtworks.xstream.annotations.*

/**
 * Instances of this class can be used to configure plugins, and to
 * communicate configuration information to brokers at the beginning
 * of a simulation. In order to use this correctly, there should be
 * exactly once instance created for each configurable plugin. This
 * should be created in plugin's XxBootStrap.groovy script.
 * @author John Collins
 */
@XStreamAlias("plugin-config")
class PluginConfig 
{
  /** Role name for this plugin. */
  @XStreamAsAttribute
  String roleName
  
  /** Instance name for this plugin, in case there are (or could be)
   *  multiple plugins in the same role. */
  @XStreamAsAttribute
  String name = ''
  
  /** Attribute-value pairs representing the configuration settings. */
  Map configuration
  
  static belongsTo = Competition
  
  static constraints = {
    configuration(nullable: false)
  }
}
