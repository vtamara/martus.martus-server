/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2004, Beneficent
Technology, Inc. (Benetech).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/
package org.martus.server.forclients;

import org.martus.util.TestCaseEnhanced;


public class TestAuthorizeLogEntry extends TestCaseEnhanced
{
	public TestAuthorizeLogEntry(String name)
	{
		super(name);
	}
	
	public void testBasics()
	{
		String date = "2004-02-15";
		String code = "1234.1234.1234.1234";
		String magic = "my Magic";
		String group = "My group";
		String ip = "1.2.3.4";
		
		String newClientLineEntry = date + AuthorizeLogEntry.FIELD_DELIMITER + code + AuthorizeLogEntry.FIELD_DELIMITER + ip + AuthorizeLogEntry.FIELD_DELIMITER + magic + AuthorizeLogEntry.FIELD_DELIMITER + group ;
		AuthorizeLogEntry entry = new AuthorizeLogEntry(newClientLineEntry);
		assertEquals("date not found?", date, entry.getDate());
		assertEquals("code not found?", code, entry.getPublicCode());
		assertEquals("ip not found?", ip, entry.getIp());
		assertEquals("magic word not found?", magic, entry.getMagicWord());
		assertEquals("group not found?", group, entry.getGroupName());
		
		assertEquals("to String didn't return same value?", newClientLineEntry, entry.toString());

		AuthorizeLogEntry entry2 = new AuthorizeLogEntry(code, magic, group);
		
		date = AuthorizeLogEntry.getISODate();
		assertEquals("date2 not found?", date, entry2.getDate());
		assertEquals("code2 not found?", code, entry2.getPublicCode());
		assertEquals("ip can not be found since we have no xmlrpc thread running", null, entry2.getIp());
		assertEquals("magic2 not found?", magic, entry2.getMagicWord());
		assertEquals("group2 not found?", group, entry2.getGroupName());
	}
	
	public void testStatics()
	{
		String date = "2004-02-05";
		String code = "1234.1234.1234.1234";
		String ip = "1.2.3.4";
		String magic = "Magic Word used";
		String group = "Richards group";
		String lineEntry = date + AuthorizeLogEntry.FIELD_DELIMITER + code + AuthorizeLogEntry.FIELD_DELIMITER + ip + AuthorizeLogEntry.FIELD_DELIMITER + magic + AuthorizeLogEntry.FIELD_DELIMITER + group;

		assertEquals("Date not extracted?", date, AuthorizeLogEntry.getDateFromLineEntry(lineEntry));
		assertEquals("code not extracted?", code, AuthorizeLogEntry.getPublicCodeFromLineEntry(lineEntry));
		assertEquals("ip not extracted?", ip, AuthorizeLogEntry.getIpFromLineEntry(lineEntry));
		assertEquals("magic not extracted?", magic, AuthorizeLogEntry.getMagicWordFromLineEntry(lineEntry));
		assertEquals("group not extracted?", group, AuthorizeLogEntry.getGroupNameFromLineEntry(lineEntry));
	}
	
	public void testExtractIpAddressOnly() throws Exception
	{
		String ip = "1.2.3.4";
		String threadId = "2345";
		String ipThread = ip + ":" + threadId;
		assertEquals("Should just return ip address", ip, AuthorizeLogEntry.extractIpAddressOnly(ipThread));
		assertNull("Should return null", AuthorizeLogEntry.extractIpAddressOnly(null));
	}
}
