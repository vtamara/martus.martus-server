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
package org.martus.server.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Vector;
import org.martus.common.ContactInfo;
import org.martus.common.LoggerInterface;
import org.martus.common.MartusUtilities;
import org.martus.common.crypto.MartusCrypto;
import org.martus.common.database.Database;
import org.martus.common.database.DatabaseKey;
import org.martus.common.database.FileDatabase;
import org.martus.common.database.ServerFileDatabase;
import org.martus.common.packet.BulletinHeaderPacket;
import org.martus.common.utilities.MartusServerUtilities;
import org.martus.server.foramplifiers.ServerForAmplifiers;
import org.martus.server.forclients.AuthorizeLog;
import org.martus.server.forclients.AuthorizeLogEntry;
import org.martus.server.forclients.ServerForClients;
import org.martus.server.main.MartusServer;
import org.martus.util.UnicodeReader;
import org.martus.util.UnicodeWriter;
import org.martus.util.Base64.InvalidBase64Exception;


public class CreateStatistics
{
	public static void main(String[] args)
	{
		try
		{
			boolean prompt = true;
			boolean deletePrevious = false;
			File dataDir = null;
			File destinationDir = null;
			File keyPairFile = null;
			File adminStartupDir = null;

			for (int i = 0; i < args.length; i++)
			{
				if(args[i].startsWith("--no-prompt"))
					prompt = false;
			
				if(args[i].startsWith("--delete-previous"))
					deletePrevious = true;
			
				String value = args[i].substring(args[i].indexOf("=")+1);
				if(args[i].startsWith("--packet-directory="))
					dataDir = new File(value);
				
				if(args[i].startsWith("--keypair"))
					keyPairFile = new File(value);
				
				if(args[i].startsWith("--destination-directory"))
					destinationDir = new File(value);

				if(args[i].startsWith("--admin-startup-directory"))
					adminStartupDir = new File(value);
			}
			
			if(destinationDir == null || dataDir == null || keyPairFile == null || adminStartupDir == null)
			{
				System.err.println("Incorrect arguments: CreateStatistics [--no-prompt] [--delete-previous] --packet-directory=<packetdir> --keypair-file=<keypair> --destination-directory=<destinationDir> --admin-startup-directory=<adminStartupConfigDir>\n");
				System.exit(2);
			}
			
			destinationDir.mkdirs();
			if(prompt)
			{
				System.out.print("Enter server passphrase:");
				System.out.flush();
			}
			
			BufferedReader reader = new BufferedReader(new UnicodeReader(System.in));
			//TODO password is a string
			String passphrase = reader.readLine();
			MartusCrypto security = MartusServerUtilities.loadCurrentMartusSecurity(keyPairFile, passphrase.toCharArray());

			new CreateStatistics(security, dataDir, destinationDir, adminStartupDir, deletePrevious);
		}
		catch(Exception e)
		{
			System.err.println("CreateStatistics.main: " + e);
			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("Done!");
		System.exit(0);
	}
	
	public CreateStatistics(MartusCrypto securityToUse, File dataDirToUse, File destinationDirToUse, File adminStartupDirToUse, boolean deletePreviousToUse) throws Exception
	{
		security = securityToUse;
		deletePrevious = deletePreviousToUse;
		packetsDir = dataDirToUse;
		destinationDir = destinationDirToUse;
		adminStartupDir = adminStartupDirToUse;
		fileDatabase = new ServerFileDatabase(dataDirToUse, security);
		fileDatabase.initialize();
		clientsThatCanUpload = MartusUtilities.loadCanUploadFile(new File(packetsDir.getParentFile(), ServerForClients.UPLOADSOKFILENAME));
		bannedClients = MartusUtilities.loadBannedClients(new File(adminStartupDir, ServerForClients.BANNEDCLIENTSFILENAME));
		clientsNotToAmplify = MartusUtilities.loadClientsNotAmplified(new File(adminStartupDir, ServerForAmplifiers.CLIENTS_NOT_TO_AMPLIFY_FILENAME));
		authorizeLog = new AuthorizeLog(security, new NullLogger(), new File(packetsDir.getParentFile(), ServerForClients.AUTHORIZELOGFILENAME));  		
		authorizeLog.loadFile();

		CreateAccountStatistics();
		CreateBulletinStatistics();
//		CreatePacketStatistics();
	}
	public class NullLogger implements LoggerInterface
	{
		public NullLogger()	{}
		public void log(String message)	{}
	}

	private void CreateAccountStatistics() throws Exception
	{
		final File accountStatsError = new File(destinationDir,ACCOUNT_STATS_FILE_NAME + ERR_EXT + CSV_EXT);
		class AccountVisitor implements Database.AccountVisitor 
		{
			public AccountVisitor(UnicodeWriter writerToUse)
			{
				writer = writerToUse;
			}
			class noContactInfoException extends IOException{};
			class HarmlessException extends IOException{};
			public void visit(String accountId)
			{
				boolean errorOccured = false;
				File accountDir = fileDatabase.getAbsoluteAccountDirectory(accountId);
				File bucket = accountDir.getParentFile();
				String publicCode = "";
				try
				{
					publicCode = MartusCrypto.computeFormattedPublicCode(accountId);
				}
				catch(Exception e)
				{
					publicCode = ERROR_MSG + " " + e;
				}
				try
				{
					String author = "";
					String organization = "";
					String email = "";
					String webpage = "";
					String phone = "";
					String address = "";

					try
					{
						File contactFile = fileDatabase.getContactInfoFile(accountId);
						if(!contactFile.exists())
							throw new noContactInfoException();
						Vector contactInfoRaw = ContactInfo.loadFromFile(contactFile);
						Vector contactInfo = ContactInfo.decodeContactInfoVectorIfNecessary(contactInfoRaw);
						int size = contactInfo.size();
						if(size>0)
						{
							String contactAccountIdInsideFile = (String)contactInfo.get(0);
							if(!security.verifySignatureOfVectorOfStrings(contactInfo, contactAccountIdInsideFile))
							{
								author = ERROR_MSG + " Signature failure contactInfo";
								throw new HarmlessException();
							}
							
							if(!contactAccountIdInsideFile.equals(accountId))
							{
								author = ERROR_MSG + " AccountId doesn't match contactInfo's AccountId";
								throw new HarmlessException();
							}			
						}
						
						if(size>2)
							author = (String)(contactInfo.get(2));
						if(size>3)
							organization = (String)(contactInfo.get(3));
						if(size>4)
							email = (String)(contactInfo.get(4));
						if(size>5)
							webpage = (String)(contactInfo.get(5));
						if(size>6)
							phone = (String)(contactInfo.get(6));
						if(size>7)
							address = (String)(contactInfo.get(7));
					}
					catch (noContactInfoException e)
					{
					}
					catch (HarmlessException e)
					{
						errorOccured = true;
					}
					catch (IOException e)
					{
						errorOccured = true;
						author = ERROR_MSG + " IO exception contactInfo";
					}
					catch(InvalidBase64Exception e)
					{
						errorOccured = true;
						author = ERROR_MSG + " InvalidBase64Exception contactInfo";
					}
					if(errorOccured)
					{
						organization = ERROR_MSG;
						email = ERROR_MSG;
						webpage = ERROR_MSG;
						phone = ERROR_MSG;
						address = ERROR_MSG;					
					
					}

					String uploadOk = isAllowedToUpload(accountId);
					String banned = isBanned(accountId);
					String notToAmplify = canAmplify(accountId);
					
					AuthorizeLogEntry clientEntry = authorizeLog.getAuthorizedClientEntry(publicCode);
					String clientAuthorizedDate = "";
					String clientIPAddress = "";
					String clientMagicWordGroup = "";
					if(clientEntry != null)
					{
						clientAuthorizedDate = clientEntry.getDate();
						clientIPAddress = clientEntry.getIp();
						clientMagicWordGroup = clientEntry.getGroupName();
					}
					
					String accountInfo = 
						getNormalizedString(publicCode) + DELIMITER +
						getNormalizedString(uploadOk) + DELIMITER +
						getNormalizedString(banned) + DELIMITER +
						getNormalizedString(notToAmplify) + DELIMITER +
						getNormalizedString(clientAuthorizedDate) + DELIMITER +
						getNormalizedString(clientIPAddress) + DELIMITER +
						getNormalizedString(clientMagicWordGroup) + DELIMITER +
						getNormalizedString(author) + DELIMITER +
						getNormalizedString(organization) + DELIMITER +
						getNormalizedString(email) + DELIMITER +
						getNormalizedString(webpage) + DELIMITER +
						getNormalizedString(phone) + DELIMITER +
						getNormalizedString(address) + DELIMITER +
						getNormalizedString(bucket.getName() + "/" + accountDir.getName()) + DELIMITER + 
						getNormalizedString(accountId);

					writer.writeln(accountInfo);
					if(errorOccured)
						writeErrorLog(accountStatsError, ACCOUNT_STATISTICS_HEADER, accountInfo);
				}
				catch(Exception e1)
				{
					try
					{
						writeErrorLog(accountStatsError, ACCOUNT_STATISTICS_HEADER, e1.getMessage());
						e1.printStackTrace();
					}
					catch(IOException e2)
					{
						e2.printStackTrace();
					}
				}
			}
			private UnicodeWriter writer;
		}

		
		
		System.out.println("Creating Account Statistics");
		File accountStats = new File(destinationDir,ACCOUNT_STATS_FILE_NAME + CSV_EXT);
		if(deletePrevious)
		{
			accountStats.delete();
			accountStatsError.delete();
		}
		
		if(accountStats.exists())
			throw new Exception("File Exists.  Please delete before running: "+accountStats.getAbsolutePath());
		if(accountStatsError.exists())
			throw new Exception("File Exists.  Please delete before running: "+accountStatsError.getAbsolutePath());
		
		UnicodeWriter writer = new UnicodeWriter(accountStats);
		writer.writeln(ACCOUNT_STATISTICS_HEADER);
		fileDatabase.visitAllAccounts(new AccountVisitor(writer));
		writer.close();

	
	}

	String isAllowedToUpload(String accountId)
	{
		if(clientsThatCanUpload.contains(accountId))
			return ACCOUNT_UPLOAD_OK_TRUE;
		return	ACCOUNT_UPLOAD_OK_FALSE;
	}
	
	String isBanned(String accountId)
	{
		if(bannedClients.contains(accountId))
			return ACCOUNT_BANNED_TRUE;
		return ACCOUNT_BANNED_FALSE;
	}
	
	String canAmplify(String accountId)
	{
		if(clientsNotToAmplify.contains(accountId))
			return ACCOUNT_AMPLIFY_FALSE;
		return ACCOUNT_AMPLIFY_TRUE;
	}
	
	
	private void CreateBulletinStatistics() throws Exception
	{
		final File bulletinStatsError = new File(destinationDir, BULLETIN_STATS_FILE_NAME + ERR_EXT + CSV_EXT);
		class BulletinVisitor implements Database.PacketVisitor
		{
			public BulletinVisitor(UnicodeWriter writerToUse)
			{
				writer = writerToUse;
			}
			
			public void visit(DatabaseKey key)
			{
				try
				{
					boolean errorOccured = false;
					if(!BulletinHeaderPacket.isValidLocalId(key.getLocalId()))
						return;
					
					String localId = key.getLocalId();
					String publicCode = "";

					try
					{
						String accountId = key.getAccountId();
						publicCode = MartusCrypto.computeFormattedPublicCode(accountId);
					}
					catch(Exception e)
					{
						errorOccured = true;
						publicCode = ERROR_MSG + " " + e;
					}

					String bulletinType = ERROR_MSG + " unknown type";
					if(key.isSealed())
						bulletinType = BULLETIN_SEALED;
					else if(key.isDraft())
						bulletinType = BULLETIN_DRAFT;
					else
						errorOccured = true;
					
					DatabaseKey burKey = MartusServerUtilities.getBurKey(key);
					String wasBurCreatedByThisServer = wasOriginalServer(burKey);
					if(wasBurCreatedByThisServer.startsWith(ERROR_MSG))
						errorOccured = true;
					String dateBulletinWasCreated = getOriginalUploadDate(burKey);
					if(dateBulletinWasCreated.startsWith(ERROR_MSG))
						errorOccured = true;
					
					String allPrivate = "";
					try
					{
						BulletinHeaderPacket bhp = MartusServer.loadBulletinHeaderPacket(fileDatabase, key, security);

						if(bhp.isAllPrivate())
							allPrivate = BULLETIN_ALL_PRIVATE_TRUE;
						else
							allPrivate = BULLETIN_ALL_PRIVATE_FALSE;
					}
					catch(Exception e1)
					{
						errorOccured = true;
						allPrivate = ERROR_MSG + " " + e1;
					}
					
					
					String bulletinInfo =  getNormalizedString(localId) + DELIMITER +
					getNormalizedString(bulletinType) + DELIMITER + 
					getNormalizedString(allPrivate) + DELIMITER + 
					getNormalizedString(wasBurCreatedByThisServer) + DELIMITER + 
					getNormalizedString(dateBulletinWasCreated) + DELIMITER + 
					getNormalizedString(publicCode);
					
					writer.writeln(bulletinInfo);
					if(errorOccured)
						writeErrorLog(bulletinStatsError, BULLETIN_STATISTICS_HEADER, bulletinInfo);
				}
				catch(IOException e)
				{
					try
					{
						writeErrorLog(bulletinStatsError, BULLETIN_STATISTICS_HEADER, e.getMessage());
						e.printStackTrace();
					}
					catch(IOException e2)
					{
						e2.printStackTrace();
					}
				}
			}

			UnicodeWriter writer;
		}
		
		System.out.println("Creating Bulletin Statistics");
		File bulletinStats = new File(destinationDir,BULLETIN_STATS_FILE_NAME + CSV_EXT);
		if(deletePrevious)
		{
			bulletinStats.delete();
			bulletinStatsError.delete();
		}
		if(bulletinStats.exists())
			throw new Exception("File Exists.  Please delete before running: "+bulletinStats.getAbsolutePath());
		if(bulletinStatsError.exists())
			throw new Exception("File Exists.  Please delete before running: "+bulletinStatsError.getAbsolutePath());
		
		UnicodeWriter writer = new UnicodeWriter(bulletinStats);
		writer.writeln(BULLETIN_STATISTICS_HEADER);
		fileDatabase.visitAllRecords(new BulletinVisitor(writer));
		writer.close();
	}

	String wasOriginalServer(DatabaseKey burKey)
	{
		String wasBurCreatedByThisServer = BULLETIN_ORIGINALLY_UPLOADED_TO_THIS_SERVER_FALSE;
		try
		{
			if(!fileDatabase.getFileForRecord(burKey).exists())
			{
				wasBurCreatedByThisServer =ERROR_MSG + " missing BUR";
			}
			else
			{
				String burString = fileDatabase.readRecord(burKey, security);
				if(burString.length()==0)
					wasBurCreatedByThisServer = ERROR_MSG + " record empty?";
				else if(MartusServerUtilities.wasBurCreatedByThisCrypto(burString, security))
					wasBurCreatedByThisServer = BULLETIN_ORIGINALLY_UPLOADED_TO_THIS_SERVER_TRUE;
			}
		}
		catch(Exception e1)
		{
			wasBurCreatedByThisServer = ERROR_MSG + " " + e1;
		}
		return wasBurCreatedByThisServer;
	}
	
	String getOriginalUploadDate(DatabaseKey burKey)
	{
		String uploadDate = "unknown";
		try
		{
			if(!fileDatabase.getFileForRecord(burKey).exists())
				return uploadDate;
			String burString = fileDatabase.readRecord(burKey, security);
			if(burString.length()!=0)
			{
				String[] burData = burString.split("\n");
				String rawDate = burData[2];
				return rawDate;
			}
		}
		catch(Exception e1)
		{
			uploadDate = ERROR_MSG + " " + e1;
		}
		return uploadDate;
	}


	
	/*	private void CreatePacketStatistics()
	{
		System.out.println("Creating Packet Statistics");

	}
*/

	void writeErrorLog(File bulletinStatsError, String headerString, String errorMsg) throws IOException
	{
		boolean includeErrorHeader = (!bulletinStatsError.exists());
		UnicodeWriter writerErr = new UnicodeWriter(bulletinStatsError, UnicodeWriter.APPEND);
		if(includeErrorHeader)
			writerErr.writeln(headerString);
		writerErr.writeln(errorMsg);
		writerErr.close();
	}
	
	String getNormalizedString(Object rawdata)
	{
		String data = (String)rawdata;
		String normalized = data.replaceAll("\"", "'");
		normalized = normalized.replaceAll("\n", " | ");
		return "\"" + normalized + "\"";
	}
	
	private boolean deletePrevious;
	MartusCrypto security;
	private File packetsDir;
	File destinationDir;
	private File adminStartupDir;
	FileDatabase fileDatabase;
	Vector clientsThatCanUpload;
	Vector bannedClients;
	Vector clientsNotToAmplify;
	AuthorizeLog authorizeLog;
	
	final String DELIMITER = ",";
	final String ERROR_MSG = "Error:";
	final String ERR_EXT = ".err";
	final String CSV_EXT = ".csv";
	final String ACCOUNT_STATS_FILE_NAME = "accounts";
	final String ACCOUNT_PUBLIC_CODE = "public code";
	final String ACCOUNT_UPLOAD_OK = "can upload";
	final String ACCOUNT_BANNED = "banned";
	final String ACCOUNT_AMPLIFY = "can amplify";
	final String ACCOUNT_DATE_AUTHORIZED = "date authorized";
	final String ACCOUNT_IP = "ip address";
	final String ACCOUNT_GROUP = "group";
	final String ACCOUNT_AUTHOR = "author name";
	final String ACCOUNT_ORGANIZATION = "organization";
	final String ACCOUNT_EMAIL = "email";
	final String ACCOUNT_WEBPAGE = "web page";
	final String ACCOUNT_PHONE = "phone";
	final String ACCOUNT_ADDRESS = "address";
	final String ACCOUNT_FOLDER = "account folder";
	final String ACCOUNT_PUBLIC_KEY = "public key";
	final String ACCOUNT_UPLOAD_OK_TRUE = "1";
	final String ACCOUNT_UPLOAD_OK_FALSE = "0";
	final String ACCOUNT_BANNED_TRUE = "1";
	final String ACCOUNT_BANNED_FALSE = "0"; 
	final String ACCOUNT_AMPLIFY_TRUE = "1";
	final String ACCOUNT_AMPLIFY_FALSE = "0";

	final String ACCOUNT_STATISTICS_HEADER = 
		getNormalizedString(ACCOUNT_PUBLIC_CODE) + DELIMITER + 
		getNormalizedString(ACCOUNT_UPLOAD_OK) + DELIMITER + 
		getNormalizedString(ACCOUNT_BANNED) + DELIMITER + 
		getNormalizedString(ACCOUNT_AMPLIFY) + DELIMITER + 
		getNormalizedString(ACCOUNT_DATE_AUTHORIZED) + DELIMITER + 
		getNormalizedString(ACCOUNT_IP) + DELIMITER + 
		getNormalizedString(ACCOUNT_GROUP) + DELIMITER + 
		getNormalizedString(ACCOUNT_AUTHOR) + DELIMITER + 
		getNormalizedString(ACCOUNT_ORGANIZATION) + DELIMITER + 
		getNormalizedString(ACCOUNT_EMAIL) + DELIMITER + 
		getNormalizedString(ACCOUNT_WEBPAGE) + DELIMITER + 
		getNormalizedString(ACCOUNT_PHONE) + DELIMITER + 
		getNormalizedString(ACCOUNT_ADDRESS) + DELIMITER + 
		getNormalizedString(ACCOUNT_FOLDER) + DELIMITER + 
		getNormalizedString(ACCOUNT_PUBLIC_KEY);

	final String BULLETIN_STATS_FILE_NAME = "bulletin";
	
	final String BULLETIN_HEADER_PACKET = "bulletin id";
	final String BULLETIN_TYPE = "bulletin type";
	final String BULLETIN_ALL_PRIVATE = "all private";
	final String BULLETIN_PUBLIC_ATTACHMENT_COUNT = "public attachments";
	final String BULLETIN_PRIVATE_ATTACHMENT_COUNT = "private attachments";
	final String BULLETIN_ORIGINALLY_UPLOADED_TO_THIS_SERVER = "original server";
	final String BULLETIN_DATE_UPLOADED = "date uploaded";
	
	final String BULLETIN_ORIGINALLY_UPLOADED_TO_THIS_SERVER_TRUE = "1";
	final String BULLETIN_ORIGINALLY_UPLOADED_TO_THIS_SERVER_FALSE = "0";
	final String BULLETIN_DRAFT = "draft";
	final String BULLETIN_SEALED = "sealed";
	final String BULLETIN_ALL_PRIVATE_TRUE = "1";
	final String BULLETIN_ALL_PRIVATE_FALSE = "0";
	
	final String BULLETIN_STATISTICS_HEADER = 
		getNormalizedString(BULLETIN_HEADER_PACKET) + DELIMITER +
		getNormalizedString(BULLETIN_TYPE) + DELIMITER +
		getNormalizedString(BULLETIN_ALL_PRIVATE) + DELIMITER +
//		getNormalizedString(BULLETIN_PUBLIC_ATTACHMENT_COUNT) + DELIMITER +
		//getNormalizedString(BULLETIN_PRIVATE_ATTACHMENT_COUNT) + DELIMITER +
		getNormalizedString(BULLETIN_ORIGINALLY_UPLOADED_TO_THIS_SERVER) + DELIMITER +
		getNormalizedString(BULLETIN_DATE_UPLOADED) + DELIMITER +
		getNormalizedString(ACCOUNT_PUBLIC_CODE);
	
}