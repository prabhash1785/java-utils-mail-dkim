/*
 * Copyright (C) 2015 Torsten Krause.
 * 
 * This file is part of 'A DKIM library for JavaMail', hereafter
 * called 'this library', identified by the following coordinates:
 * 
 *    groupID: net.markenwerk
 *    artifactId: utils-mail-dkim
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 * 
 * See the LICENSE and NOTICE files in the root directory for further
 * information.
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:
 *  
 *    Copyright 2008 The Apache Software Foundation or its licensors, as
 *    applicable.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *    A licence was granted to the ASF by Florian Sager on 30 November 2008
 */
package net.markenwerk.utils.mail.dkim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import sun.misc.BASE64Encoder;

import com.sun.mail.util.QPEncoderStream;

/*
 * @author Florian Sager, http://www.agitos.de, 22.11.2008
 */

public class DkimUtil {

	protected static String[] splitHeader(String header) throws DkimException {
		int colonPos = header.indexOf(':');
		if (colonPos==-1) {
			throw new DkimException("The header string "+header+" is no valid RFC 822 header-line");
		}
		return new String[]{header.substring(0, colonPos), header.substring(colonPos+1)};
	}

	protected static String concatArray(ArrayList l, String separator) {
		StringBuffer buf = new StringBuffer();
		Iterator iter = l.iterator();
		while (iter.hasNext()) {
			buf.append(iter.next()).append(separator);
		}

		return buf.substring(0, buf.length() - separator.length());
	}

	protected static boolean isValidDomain(String domainname) {
		Pattern pattern = Pattern.compile("(.+)\\.(.+)");
		Matcher matcher = pattern.matcher(domainname);
		return matcher.matches();
	}

	// FSTODO: converts to "platforms default encoding" might be wrong ?
	protected static String QuotedPrintable(String s) {

		try {
			ByteArrayOutputStream boas =   new ByteArrayOutputStream();
			QPEncoderStream encodeStream = new QPEncoderStream(boas);
			encodeStream.write(s.getBytes());
			
			String encoded = boas.toString();
			encoded = encoded.replaceAll(";", "=3B");
			encoded = encoded.replaceAll(" ", "=20");

			return encoded;

		} catch (IOException ioe) {}

		return null;
	}

	protected static String base64Encode(byte[] b) {
		BASE64Encoder base64Enc = new BASE64Encoder();
		String encoded = base64Enc.encode(b);
		// remove unnecessary linefeeds after 76 characters
		encoded = encoded.replace("\n", ""); // Linux+Win
		return encoded.replace("\r", ""); // Win --> FSTODO: select Encoder without line termination 
	}

	public boolean checkDNSForPublickey(String signingDomain, String selector) throws DkimException {

		Hashtable<String, String> env = new Hashtable<String, String>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        String recordname = selector+"._domainkey."+signingDomain;
        String value = null;

        try {
        	DirContext dnsContext = new InitialDirContext(env);

        	javax.naming.directory.Attributes attribs = dnsContext.getAttributes(recordname, new String[] {"TXT"});
        	javax.naming.directory.Attribute txtrecord = attribs.get("txt");
        	
        	if (txtrecord == null) {
        		throw new DkimException("There is no TXT record available for "+recordname);
        	}

        	// "v=DKIM1; g=*; k=rsa; p=MIGfMA0G ..."
        	value = (String) txtrecord.get();

        } catch (NamingException ne) {
        	throw new DkimException("Selector lookup failed", ne);
        }
        
        if (value == null) {
        	throw new DkimException("Value of RR "+recordname+" couldn't be retrieved");
        }

        // try to read public key from RR
        String[] tags = value.split(";");
        for (String tag : tags) {
        	tag = tag.trim();
        	if (tag.startsWith("p=")) {
        		
        		try {
	        		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
	
	        		// decode public key, FSTODO: convert to DER format
	        		PKCS8EncodedKeySpec pubSpec = new PKCS8EncodedKeySpec(tag.substring(2).getBytes());
	        		RSAPrivateKey pubKey = (RSAPrivateKey) keyFactory.generatePublic(pubSpec);
        		} catch (NoSuchAlgorithmException nsae) {
        			throw new DkimException("RSA algorithm not found by JVM");
        		} catch (InvalidKeySpecException ikse) {
        			throw new DkimException("The public key "+tag+" in RR "+recordname+" couldn't be decoded.");
        		}
        		
        		// FSTODO: create test signature with privKey and test validation with pubKey to check on a valid key pair

        		return true;
        	}
		}

        throw new DkimException("No public key available in "+recordname);
	}

}