/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j.xml;

import org.apache.log4j.LogManager;
import org.apache.log4j.config.PropertySetter;
import org.apache.log4j.helpers.FileWatchdog;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.plugins.Plugin;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.LoggerRepositoryEx;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.FactoryConfigurationError;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class extends DOMConfigurator to support
 * processing of plugin elements.
 */
public class PluginConfigurator extends
        org.apache.log4j.xml.DOMConfigurator {

    /**
     * Create new instance.
     */
    public PluginConfigurator() {
        super();
    }

    /**
       Configure log4j using a <code>configuration</code> element as
       defined in the log4j.dtd.

    */
    static
    public
    void configure (final Element element) {
      PluginConfigurator configurator = new PluginConfigurator();
      configurator.doConfigure(element,  LogManager.getLoggerRepository());
    }

    /**
       A static version of {@link #doConfigure(String, LoggerRepository)}.  */
    static
    public
    void configure(final String filename) throws FactoryConfigurationError {
      new PluginConfigurator().doConfigure(filename, 
                        LogManager.getLoggerRepository());
    }

    /**
       A static version of {@link #doConfigure(URL, LoggerRepository)}.
     */
    static
    public
    void configure(final URL url) throws FactoryConfigurationError {
      new PluginConfigurator().doConfigure(url, LogManager.getLoggerRepository());
    }

    /**
       Read the configuration file <code>configFilename</code> if it
       exists. Moreover, a thread will be created that will periodically
       check if <code>configFilename</code> has been created or
       modified. The period is determined by the <code>delay</code>
       argument. If a change or file creation is detected, then
       <code>configFilename</code> is read to configure log4j.

        @param configFilename A log4j configuration file in XML format.
        @param delay The delay in milliseconds to wait between each check.
    */
    static
    public
    void configureAndWatch(final String configFilename, final long delay) {
      PluginWatchdog xdog = new PluginWatchdog(configFilename);
      xdog.setDelay(delay);
      xdog.start();
    }



   /**
       Like {@link #configureAndWatch(String, long)} except that the
       default delay as defined by {@link FileWatchdog#DEFAULT_DELAY} is
       used.

       @param configFilename A log4j configuration file in XML format.

    */
    static
    public
    void configureAndWatch(final String configFilename) {
      configureAndWatch(configFilename, FileWatchdog.DEFAULT_DELAY);
    }


    /**
     * {@inheritDoc}
     */
    protected void parse(final Element element) {
        super.parse(element);
        if (repository instanceof LoggerRepositoryEx) {
            List plugins = new ArrayList();
            createPlugins((LoggerRepositoryEx) repository, plugins, element);
            for (Iterator iter = plugins.iterator(); iter.hasNext();) {
                ((Plugin) iter.next()).activateOptions();
            }
        }
    }

    /**
     * Iterates over child nodes looking for plugin elements
     * and constructs the plugins.
     * @param repox repository
     * @param plugins list to receive created plugins
     * @param node parent node
     */
    private void createPlugins(
            final LoggerRepositoryEx repox,
            final List plugins, final Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                    if ("plugin".equals(child.getNodeName())) {
                        createPlugin(repox, plugins, (Element) child);
                    }
                    break;

                case Node.ENTITY_REFERENCE_NODE:
                    createPlugins(repox, plugins, child);
                    break;

                default:
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Iterates over children of plugin node looking for
     * param elements.
     * @param propSetter property setter
     * @param parent parent node
     */
    private void setParams(final PropertySetter propSetter,
                           final Node parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                    if ("param".equals(child.getNodeName())) {
                        setParameter((Element) child, propSetter);
                    }
                    break;

                case Node.ENTITY_REFERENCE_NODE:
                    setParams(propSetter, child);
                    break;

                default:
            }
            child = child.getNextSibling();
        }


    }

    /**
     * Creates a plugin
     * @param repox repository
     * @param plugins list of receive created plugins
     * @param element plugin element
     */
    private void createPlugin(
            final LoggerRepositoryEx repox,
            final List plugins,
            final Element element) {
        String className = element.getAttribute("class");

        if (className.length() > 0)
            try {
                LogLog.debug(
                        "About to instantiate plugin of type [" + className + "]");

                Plugin plugin = (Plugin)
                        OptionConverter.instantiateByClassName(
                                className, org.apache.log4j.plugins.Plugin.class, null);

                String pluginName = subst(element.getAttribute("name"));

                if (pluginName.length() == 0) {
                    LogLog.warn(
                            "No plugin name given for plugin of type " + className + "].");
                } else {
                    plugin.setName(pluginName);
                    LogLog.debug("plugin named as [" + pluginName + "]");
                }

                PropertySetter propSetter = new PropertySetter(plugin);
                setParams(propSetter, element);

                repox.getPluginRegistry().addPlugin(plugin);
                plugin.setLoggerRepository(repox);

                LogLog.debug("Pushing plugin on to the object stack.");
                plugins.add(plugin);
            } catch (Exception oops) {
                LogLog.error(
                        "Could not create a plugin. Reported error follows.", oops);
            }
    }

    /**
     * File change monitor class.
     */
    private static class PluginWatchdog extends FileWatchdog {

        /**
         * Construct new instance.
         * @param filename
         */
      public PluginWatchdog(final String filename) {
        super(filename);
      }

      /**
         Call configure with the
         <code>filename</code> to reconfigure log4j. */
      public
      void doOnChange() {
        new PluginConfigurator().doConfigure(filename,
                          LogManager.getLoggerRepository());
      }
    }


}
