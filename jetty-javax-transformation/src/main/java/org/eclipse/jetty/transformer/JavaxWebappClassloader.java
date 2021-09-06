//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.transformer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public class JavaxWebappClassloader extends WebAppClassLoader
{

    private static final JavaxTransfomer INSTANCE = new JavaxTransfomer();

    public JavaxWebappClassloader(Context context) throws IOException
    {
        super(context);
        addTransformer(INSTANCE);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException
    {
        if ("META-INF/services/jakarta.servlet.ServletContainerInitializer".equals(name))
        {
            // we can mix with javax.servlet if any
            Enumeration<URL> enumeration =
                    super.getResources("META-INF/services/javax.servlet.ServletContainerInitializer");
            List<URL> urls = Collections.list(enumeration);
            urls.addAll(Collections.list(super.getResources(name)));
            return Collections.enumeration(urls);
        }
        return super.getResources(name);
    }

    @Override
    public URL getResource(String name)
    {
        return super.getResource(name);
    }

    @Override
    protected Class<?> loadAsResource(String name, boolean checkSystemResource) throws ClassNotFoundException
    {
        return super.loadAsResource(name, checkSystemResource);
    }

    @Override
    public URL findResource(String name)
    {
        return super.findResource(name);
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException
    {
        return super.findResources(name);
    }

    static class JavaxTransfomer implements ClassFileTransformer
    {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
        {
            try
            {
                ClassReader cr = new ClassReader(new ByteArrayInputStream(classfileBuffer));
                ClassWriter cw = new ClassWriter(0);

                ClassRemapper classRemapper = new ClassRemapper(cw, new JavaxRemapper());

                cr.accept(classRemapper, ClassReader.EXPAND_FRAMES);

                return cw.toByteArray();
            }
            catch (Exception e)
            {
                IllegalClassFormatException icfe = new IllegalClassFormatException(e.getMessage());
                icfe.initCause(e);
                throw icfe;
            }
        }

    }

    static class JavaxRemapper extends Remapper
    {

        private static final String JAVAX_SERVLET = "javax/servlet/";

        private static final String JAVAX_EL = "javax/el/";

        private static final String JAKARTA_SERVLET = "jakarta/servlet/";

        private static final String JAKARTA_EL = "jakarta/el/";

        @Override
        public String map(String internalName)
        {
            return nameMapping(internalName);
        }

        @Override
        public String mapType(String internalName)
        {
            String mappedName = nameMapping(internalName);
            return mappedName.equals(internalName) ? super.mapType(internalName) : mappedName;
        }

        @Override
        public String[] mapTypes(String[] internalNames)
        {
            return Arrays.stream(internalNames)
                    .map(this::nameMapping)
                    .toArray(String[]::new);
        }

        @Override
        public String mapSignature(String signature, boolean typeSignature)
        {
            return super.mapSignature(signature, typeSignature);
        }

        @Override
        public String mapPackageName(String name)
        {
            return super.mapPackageName(name);
        }

        private String nameMapping(String internalName)
        {
            if (internalName.startsWith(JAVAX_SERVLET))
            {
                return JAKARTA_SERVLET + internalName.substring(JAVAX_SERVLET.length());
            }
            if (internalName.startsWith(JAVAX_EL))
            {
                return JAKARTA_EL + internalName.substring(JAVAX_EL.length());
            }
            return internalName;
        }

    }

}
