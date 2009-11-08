/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.configuration2;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Iterator;

import org.apache.commons.configuration2.event.ConfigurationErrorEvent;
import org.apache.commons.configuration2.event.ConfigurationErrorListener;
import org.apache.commons.configuration2.event.EventSource;
import org.apache.commons.configuration2.expr.ExpressionEngine;
import org.apache.commons.configuration2.flat.AbstractFlatConfiguration;
import org.apache.commons.configuration2.fs.DefaultFileSystem;
import org.apache.commons.configuration2.fs.FileSystem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Miscellaneous utility methods for configurations.
 *
 * @see ConfigurationConverter Utility methods to convert configurations.
 *
 * @author <a href="mailto:herve.quiroz@esil.univ-mrs.fr">Herve Quiroz</a>
 * @author <a href="mailto:oliver.heger@t-online.de">Oliver Heger</a>
 * @author Emmanuel Bourg
 * @version $Revision$, $Date$
 */
public final class ConfigurationUtils
{
    /** Constant for the file URL protocol.*/
    static final String PROTOCOL_FILE = "file";

    /** Constant for the resource path separator.*/
    static final String RESOURCE_PATH_SEPARATOR = "/";

    /** Constant for the name of the clone() method.*/
    private static final String METHOD_CLONE = "clone";

    /** The logger.*/
    private static Log log = LogFactory.getLog(ConfigurationUtils.class.getName());

    /**
     * Private constructor. Prevents instances from being created.
     */
    private ConfigurationUtils()
    {
        // to prevent instanciation...
    }

    /**
     * Dump the configuration key/value mappings to some ouput stream.
     *
     * @param configuration the configuration
     * @param out the output stream to dump the configuration to
     */
    public static void dump(Configuration configuration, PrintStream out)
    {
        dump(configuration, new PrintWriter(out));
    }

    /**
     * Dump the configuration key/value mappings to some writer.
     *
     * @param configuration the configuration
     * @param out the writer to dump the configuration to
     */
    public static void dump(Configuration configuration, PrintWriter out)
    {
        Iterator<String> keys = configuration.getKeys();
        while (keys.hasNext())
        {
            String key = keys.next();
            Object value = configuration.getProperty(key);
            out.print(key);
            out.print("=");
            out.print(value);

            if (keys.hasNext())
            {
                out.println();
            }
        }

        out.flush();
    }

    /**
     * Get a string representation of the key/value mappings of a
     * configuration.
     *
     * @param configuration the configuration
     * @return a string representation of the configuration
     */
    public static String toString(Configuration configuration)
    {
        StringWriter writer = new StringWriter();
        dump(configuration, new PrintWriter(writer));
        return writer.toString();
    }

    /**
     * <p>Copy all properties from the source configuration to the target
     * configuration. Properties in the target configuration are replaced with
     * the properties with the same key in the source configuration.</p>
     * <p><em>Note:</em> This method is not able to handle some specifics of
     * configurations derived from <code>AbstractConfiguration</code> (e.g.
     * list delimiters). For a full support of all of these features the
     * <code>copy()</code> method of <code>AbstractConfiguration</code> should
     * be used. In a future release this method might become deprecated.</p>
     *
     * @param source the source configuration
     * @param target the target configuration
     * @since 1.1
     */
    public static void copy(Configuration source, Configuration target)
    {
        Iterator<String> keys = source.getKeys();
        while (keys.hasNext())
        {
            String key = keys.next();
            target.setProperty(key, source.getProperty(key));
        }
    }

    /**
     * <p>Append all properties from the source configuration to the target
     * configuration. Properties in the source configuration are appended to
     * the properties with the same key in the target configuration.</p>
     * <p><em>Note:</em> This method is not able to handle some specifics of
     * configurations derived from <code>AbstractConfiguration</code> (e.g.
     * list delimiters). For a full support of all of these features the
     * <code>copy()</code> method of <code>AbstractConfiguration</code> should
     * be used. In a future release this method might become deprecated.</p>
     *
     * @param source the source configuration
     * @param target the target configuration
     * @since 1.1
     */
    public static void append(Configuration source, Configuration target)
    {
        Iterator<String> keys = source.getKeys();
        while (keys.hasNext())
        {
            String key = keys.next();
            target.addProperty(key, source.getProperty(key));
        }
    }

    /**
     * Converts the passed in configuration to a hierarchical one. If the
     * configuration is already hierarchical, it is directly returned. Otherwise
     * all properties are copied into a new hierarchical configuration.
     *
     * @param conf the configuration to convert
     * @return the new hierarchical configuration (the result is <b>null</b> if
     * and only if the passed in configuration is <b>null</b>)
     * @since 1.3
     */
    public static HierarchicalConfiguration convertToHierarchical(Configuration conf)
    {
        // todo to be changed into convertToHierarchical(conf, null) when HierarchicalConfiguration is removed

        if (conf == null)
        {
            return null;
        }

        if (conf instanceof HierarchicalConfiguration)
        {
            return (HierarchicalConfiguration) conf;
        }
        else
        {
            HierarchicalConfiguration hc = new HierarchicalConfiguration();
            // Workaround for problem with copy()
            boolean delimiterParsingStatus = hc.isDelimiterParsingDisabled();
            hc.setDelimiterParsingDisabled(true);
            ConfigurationUtils.copy(conf, hc);
            hc.setDelimiterParsingDisabled(delimiterParsingStatus);
            return hc;
        }
    }

    /**
     * Converts the passed in <code>Configuration</code> object to a
     * hierarchical one using the specified <code>ExpressionEngine</code>. This
     * conversion works by adding the keys found in the configuration to a newly
     * created hierarchical configuration. When adding new keys to a
     * hierarchical configuration the keys are interpreted by its
     * <code>ExpressionEngine</code>. If they contain special characters (e.g.
     * brackets) that are treated in a special way by the default expression
     * engine, it may be necessary using a specific engine that can deal with
     * such characters. Otherwise <b>null</b> can be passed in for the
     * <code>ExpressionEngine</code>; then the default expression engine is
     * used. If the passed in configuration is already hierarchical, it is
     * directly returned. (However, the <code>ExpressionEngine</code> is set if
     * it is not <b>null</b>.) Otherwise all properties are copied into a new
     * hierarchical configuration.
     *
     * @param conf the configuration to convert
     * @param engine the <code>ExpressionEngine</code> for the hierarchical
     *        configuration or <b>null</b> for the default
     * @return the new hierarchical configuration (the result is <b>null</b> if
     *         and only if the passed in configuration is <b>null</b>)
     * @since 1.6
     */
    public static AbstractHierarchicalConfiguration<?> convertToHierarchical(Configuration conf, ExpressionEngine engine)
    {
        if (conf == null)
        {
            return null;
        }

        if (conf instanceof AbstractHierarchicalConfiguration<?> && !(conf instanceof AbstractFlatConfiguration))
        {
            AbstractHierarchicalConfiguration<?> hc = (AbstractHierarchicalConfiguration<?>) conf;
            if (engine != null)
            {
                hc.setExpressionEngine(engine);
            }

            return hc;
        }
        else
        {
            AbstractHierarchicalConfiguration<?> hc = new InMemoryConfiguration();
            if (engine != null)
            {
                hc.setExpressionEngine(engine);
            }

            // Workaround for problem with copy()
            boolean delimiterParsingStatus = hc.isDelimiterParsingDisabled();
            hc.setDelimiterParsingDisabled(true);
            hc.append(conf);
            hc.setDelimiterParsingDisabled(delimiterParsingStatus);
            return hc;
        }
    }

    /**
     * Clones the given configuration object if this is possible. If the passed
     * in configuration object implements the <code>Cloneable</code>
     * interface, its <code>clone()</code> method will be invoked. Otherwise
     * an exception will be thrown.
     *
     * @param config the configuration object to be cloned (can be <b>null</b>)
     * @return the cloned configuration (<b>null</b> if the argument was
     * <b>null</b>, too)
     * @throws ConfigurationRuntimeException if cloning is not supported for
     * this object
     * @since 1.3
     */
    public static Configuration cloneConfiguration(Configuration config)
            throws ConfigurationRuntimeException
    {
        if (config == null)
        {
            return null;
        }
        else
        {
            try
            {
                return (Configuration) clone(config);
            }
            catch (CloneNotSupportedException cnex)
            {
                throw new ConfigurationRuntimeException(cnex);
            }
        }
    }

    /**
     * An internally used helper method for cloning objects. This implementation
     * is not very sophisticated nor efficient. Maybe it can be replaced by an
     * implementation from Commons Lang later. The method checks whether the
     * passed in object implements the <code>Cloneable</code> interface. If
     * this is the case, the <code>clone()</code> method is invoked by
     * reflection. Errors that occur during the cloning process are re-thrown as
     * runtime exceptions.
     *
     * @param obj the object to be cloned
     * @return the cloned object
     * @throws CloneNotSupportedException if the object cannot be cloned
     */
    public static Object clone(Object obj) throws CloneNotSupportedException
    {
        if (obj instanceof Cloneable)
        {
            try
            {
                Method m = obj.getClass().getMethod(METHOD_CLONE);
                return m.invoke(obj);
            }
            catch (NoSuchMethodException nmex)
            {
                throw new CloneNotSupportedException("No clone() method found for class" + obj.getClass().getName());
            }
            catch (IllegalAccessException iaex)
            {
                throw new ConfigurationRuntimeException(iaex);
            }
            catch (InvocationTargetException itex)
            {
                throw new ConfigurationRuntimeException(itex);
            }
        }
        else
        {
            throw new CloneNotSupportedException(obj.getClass().getName() + " does not implement Cloneable");
        }
    }

    /**
     * Constructs a URL from a base path and a file name. The file name can
     * be absolute, relative or a full URL. If necessary the base path URL is
     * applied.
     *
     * @param basePath the base path URL (can be <b>null</b>)
     * @param file the file name
     * @return the resulting URL
     * @throws MalformedURLException if URLs are invalid
     */
    public static URL getURL(String basePath, String file) throws MalformedURLException
    {
        return FileSystem.getDefaultFileSystem().getURL(basePath, file);
    }

    /**
     * Return the location of the specified resource by searching the user home
     * directory, the current classpath and the system classpath.
     *
     * @param name the name of the resource
     *
     * @return the location of the resource
     */
    public static URL locate(String name)
    {
        return locate(null, name);
    }

    /**
     * Return the location of the specified resource by searching the user home
     * directory, the current classpath and the system classpath.
     *
     * @param base the base path of the resource
     * @param name the name of the resource
     *
     * @return the location of the resource
     */
    public static URL locate(String base, String name)
    {
        return locate(FileSystem.getDefaultFileSystem(), base, name);
    }

    /**
     * Return the location of the specified resource by searching the user home
     * directory, the current classpath and the system classpath.
     *
     * @param fileSystem the FileSystem to use.
     * @param base the base path of the resource
     * @param name the name of the resource
     *
     * @return the location of the resource
     */
    public static URL locate(FileSystem fileSystem, String base, String name)
    {
        if (log.isDebugEnabled())
        {
            StringBuilder buf = new StringBuilder();
            buf.append("ConfigurationUtils.locate(): base is ").append(base);
            buf.append(", name is ").append(name);
            log.debug(buf.toString());
        }

        if (name == null)
        {
            // undefined, always return null
            return null;
        }

        // attempt to create an URL directly

        URL url = fileSystem.locateFromURL(base, name);

        // attempt to load from an absolute path
        if (url == null)
        {
            File file = new File(name);
            if (file.isAbsolute() && file.exists()) // already absolute?
            {
                try
                {
                    url = file.toURI().toURL();
                    log.debug("Loading configuration from the absolute path " + name);
                }
                catch (MalformedURLException e)
                {
                    log.warn("Could not obtain URL from file", e);
                }
            }
        }

        // attempt to load from the base directory
        if (url == null)
        {
            try
            {
                File file = DefaultFileSystem.constructFile(base, name);
                if (file != null && file.exists())
                {
                    url = file.toURI().toURL();
                }

                if (url != null)
                {
                    log.debug("Loading configuration from the path " + file);
                }
            }
            catch (MalformedURLException e)
            {
                log.warn("Could not obtain URL from file", e);
            }
        }

        // attempt to load from the user home directory
        if (url == null)
        {
            try
            {
                File file = DefaultFileSystem.constructFile(System.getProperty("user.home"), name);
                if (file != null && file.exists())
                {
                    url = file.toURI().toURL();
                }

                if (url != null)
                {
                    log.debug("Loading configuration from the home path " + file);
                }

            }
            catch (MalformedURLException e)
            {
                log.warn("Could not obtain URL from file", e);
            }
        }

        // attempt to load from classpath
        if (url == null)
        {
            url = locateFromClasspath(name);
        }
        return url;
    }

    /**
     * Tries to find a resource with the given name in the classpath.
     * @param resourceName the name of the resource
     * @return the URL to the found resource or <b>null</b> if the resource
     * cannot be found
     */
    static URL locateFromClasspath(String resourceName)
    {
        URL url = null;
        // attempt to load from the context classpath
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null)
        {
            url = loader.getResource(resourceName);

            if (url != null)
            {
                log.debug("Loading configuration from the context classpath (" + resourceName + ")");
            }
        }

        // attempt to load from the system classpath
        if (url == null)
        {
            url = ClassLoader.getSystemResource(resourceName);

            if (url != null)
            {
                log.debug("Loading configuration from the system classpath (" + resourceName + ")");
            }
        }
        return url;
    }

    /**
     * Tries to convert the specified base path and file name into a file object.
     * This method is called e.g. by the save() methods of file based
     * configurations. The parameter strings can be relative files, absolute
     * files and URLs as well. This implementation checks first whether the passed in
     * file name is absolute. If this is the case, it is returned. Otherwise
     * further checks are performed whether the base path and file name can be
     * combined to a valid URL or a valid file name. <em>Note:</em> The test
     * if the passed in file name is absolute is performed using
     * <code>java.io.File.isAbsolute()</code>. If the file name starts with a
     * slash, this method will return <b>true</b> on Unix, but <b>false</b> on
     * Windows. So to ensure correct behavior for relative file names on all
     * platforms you should never let relative paths start with a slash. E.g.
     * in a configuration definition file do not use something like that:
     * <pre>
     * &lt;properties fileName="/subdir/my.properties"/&gt;
     * </pre>
     * Under Windows this path would be resolved relative to the configuration
     * definition file. Under Unix this would be treated as an absolute path
     * name.
     *
     * @param basePath the base path
     * @param fileName the file name
     * @return the file object (<b>null</b> if no file can be obtained)
     */
    public static File getFile(String basePath, String fileName)
    {
        // Check if the file name is absolute
        File f = new File(fileName);
        if (f.isAbsolute())
        {
            return f;
        }

        // Check if URLs are involved
        URL url;
        try
        {
            url = new URL(new URL(basePath), fileName);
        }
        catch (MalformedURLException mex1)
        {
            try
            {
                url = new URL(fileName);
            }
            catch (MalformedURLException mex2)
            {
                url = null;
            }
        }

        if (url != null)
        {
            return fileFromURL(url);
        }

        return DefaultFileSystem.constructFile(basePath, fileName);
    }

    /**
     * Tries to convert the specified URL to a file object. If this fails,
     * <b>null</b> is returned.
     *
     * @param url the URL
     * @return the resulting file object
     */
    public static File fileFromURL(URL url)
    {
        if (PROTOCOL_FILE.equals(url.getProtocol()))
        {
            return new File(URLDecoder.decode(url.getPath()));
        }
        else
        {
            return null;
        }
    }

    /**
     * Enables runtime exceptions for the specified configuration object. This
     * method can be used for configuration implementations that may face errors
     * on normal property access, e.g. <code>DatabaseConfiguration</code> or
     * <code>JNDIConfiguration</code>. Per default such errors are simply
     * logged and then ignored. This implementation will register a special
     * <code>{@link ConfigurationErrorListener}</code> that throws a runtime
     * exception (namely a <code>ConfigurationRuntimeException</code>) on
     * each received error event.
     *
     * @param src the configuration, for which runtime exceptions are to be
     * enabled; this configuration must be derived from
     * <code>{@link EventSource}</code>
     */
    public static void enableRuntimeExceptions(Configuration src)
    {
        if (!(src instanceof EventSource))
        {
            throw new IllegalArgumentException("Configuration must be derived from EventSource!");
        }
        ((EventSource) src).addErrorListener(new ConfigurationErrorListener()
        {
            public void configurationError(ConfigurationErrorEvent event)
            {
                // Throw a runtime exception
                throw new ConfigurationRuntimeException(event.getCause());
            }
        });
    }
}