package org.mifosplatform.mpesa.service;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import org.hibernate.validator.internal.metadata.aggregated.ValidatableParametersMetaData;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.mifosplatform.mpesa.configuration.ClientHelper;
import org.mifosplatform.mpesa.domain.Mpesa;
import org.mifosplatform.mpesa.domain.MpesaBranchMapping;
import org.mifosplatform.mpesa.repository.MpesaBranchMappingRepository;
import org.mifosplatform.mpesa.repository.MpesaBridgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

@Service
public class MpesaBridgeServiceImpl implements MpesaBridgeService {

	@Value("${mifosurl}")
	private String mifosurl;

	@Value("${mifosusername}")
	private String mifosusername;

	@Value("${mifospassword}")
	private String mifospassword;

	@Value("${tenantIdentifier}")
	private String tenantIdentifier;

	private final Logger logger = LoggerFactory
			.getLogger(MpesaBridgeServiceImpl.class);

	private final MpesaBridgeRepository mpesaBridgeRepository;
	private final MpesaBranchMappingRepository mpesaBranchMappingRepository;

	@Autowired
	public MpesaBridgeServiceImpl(
			final MpesaBridgeRepository mpesaBridgeRepository, final MpesaBranchMappingRepository mpesaBranchMappingRepository ) {
		super();
		this.mpesaBridgeRepository = mpesaBridgeRepository;
		this.mpesaBranchMappingRepository = mpesaBranchMappingRepository;
	}

	@Override
	@Transactional
	public String storeTransactionDetails(final Long id, final String origin,
			final String dest, final String tStamp, final String text,
			final String user, final String pass, final String mpesaCode,
			final String mpesaAccount, final String mobileNo,
			final Date txnDate, final String txnTime,
			final BigDecimal mpesaAmount, final String sender,
			final String mpesaTxnType, Long officeId) {
		
		    Mpesa mpesa = null;
		    Mpesa response = null;
		    String responseData = "";
		
		    boolean isAccountNoFromExcel = false;     //We are getting account no from post request and excel as well so for differentiating it.
		
		
	    DateFormat source = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
	    Date newDate=null;  // new transaction date after formatting 
	    
		try {
			if(officeId == 0){                           //if office id is =0 means request comes from safaricom which doesn't contains office id initially
			    
				MpesaBranchMapping getOfficeDetails = this.mpesaBranchMappingRepository.getOfficeIdFromDestNumber(dest);
				if(getOfficeDetails != null){
					officeId = getOfficeDetails.getOffice_id();  //if post request comes then we are mapping dest id in mapping table and getting office Id           		
				}
				
				newDate = source.parse(tStamp);
			    //client mapping is done based on national id and mobile number 
			}else{
				newDate = txnDate; 
				isAccountNoFromExcel = true;  // else contains it comes from file upload and bidefualt office is - headoffice , user can changes it. but it  
			}                                 // contains office compulsory
		} catch (ParseException e1) {
			e1.printStackTrace();
		}
		
			 
		List<Mpesa> validateForTransactionId = mpesaBridgeRepository.validateForTransactionId(mpesaCode);
		
		if(validateForTransactionId.size()<0 || validateForTransactionId.isEmpty())
		{
		  try {
			if (id != null && mpesaCode != null
					&& !mpesaCode.equalsIgnoreCase("")) {
				mpesa = new Mpesa();
				mpesa.setIpnId(id);
				mpesa.setOrigin(origin);
				mpesa.setDestination(dest);
				mpesa.setTimeStamp(tStamp);
				mpesa.setTestMessage(text);
				mpesa.setUser(user);
				mpesa.setPassword(pass);
				mpesa.setTransactionCode(mpesaCode);
				mpesa.setAccountName(mpesaAccount);
				mpesa.setMobileNo(mobileNo);
				mpesa.setTransactionDate(newDate);
				mpesa.setTransactionTime(txnTime);
				mpesa.setTransactionAmount(mpesaAmount);
				mpesa.setSender(sender);
				mpesa.setType(mpesaTxnType);
				mpesa.setOfficeId(officeId);
				String accountNo = null;
				if (isAccountNoFromExcel == true) {  //if true means account no coming from excel sheet 
					if (text.contains("Acc. ")) {
						accountNo = text.substring(text.indexOf("Acc.") + "Acc.".length()).trim();
					}
				} else { // isAccountNoFromExcel is false so its coming from post request 
					if (text.contains("Account Number")) {
						String accNum = text.substring(text.indexOf("Account Number")+ "Account Number".length()).trim();						
						accountNo = accNum.substring(0, accNum.indexOf(" "));
					}
				}
				String MobileNo = null;
				if((mobileNo != null) && ! (mobileNo.isEmpty())){
					MobileNo = mobileNo.substring(3, mobileNo.length());
				}
				String result = branchMap(MobileNo, accountNo, officeId);
				String data[] = result.split("=");
				mpesa.setStatus(data[2]);
				mpesa.setClientName(data[0]);
				mpesa.setClientExternalId(data[4]);
				if(officeId == 0){					
				if (!data[1].equals(" ")) {
					mpesa.setOfficeId(Long.parseLong(data[1]));
				}
				}				
				if (!data[3].equals(" ")) {
					mpesa.setClientId(Long.parseLong(data[3]));
				}
				response = this.mpesaBridgeRepository.save(mpesa);
				if (response != null) {
					responseData = "Thank you for your payment";
				}
			} else {
				logger.info("Empty Parameter passed");
				responseData = "Empty Parameter passed";
			}
		} catch (Exception e) {
			logger.error("Exception while storeTransactionDetails " + e);
			return responseData = e.getMessage();
		}
	  }
	 else {
		    responseData =  mpesaCode;
		}
		return responseData;
	}

		private String loginIntoServerAndGetBase64EncodedAuthenticationKey() {
		final String loginURL = mifosurl
				+ "/mifosng-provider/api/v1/authentication?username="
				+ mifosusername + "&password=" + mifospassword;
		System.out.println(loginURL);
		Client client = null;
		WebResource webResource = null;
		String authenticationKey = null;
		try {
			client = ClientHelper.createClient();
			webResource = client.resource(loginURL);

			ClientResponse response = webResource
					.header("X-mifos-Platform-TenantId", tenantIdentifier)
					.header("Content-Type", "application/json")
					.post(ClientResponse.class);
			String responseData = response.getEntity(String.class);
			JSONObject rootObj = (JSONObject) JSONValue
					.parseWithException(responseData);
			if (rootObj != null && !rootObj.equals("")) {
				authenticationKey = rootObj.get(
						"base64EncodedAuthenticationKey").toString();
			}
		} catch (Exception e) {
			logger.error("Exception while loginIntoServerAndGetBase64EncodedAuthenticationKey "
					+ e);
		}
		return authenticationKey;
	}

		@Override
	public String branchMap(String MobileNo, String accountNo, Long officeId) {
		Boolean externalIdSearch = false;
		Client client = null;
		String authenticationKey = null;
		WebResource webResource = null;
		String details = "";
		String clientExternalId = null;
		String officeExternalId = null;
		try {
			authenticationKey = loginIntoServerAndGetBase64EncodedAuthenticationKey();
			if (accountNo != null && accountNo != "" && officeId != 0) {
				client = ClientHelper.createClient();
				
				if(officeId != null && officeId !=0){
					webResource = client.resource(mifosurl+ "/mifosng-provider/api/v1/offices/" +officeId);
					ClientResponse response = webResource.header("X-mifos-Platform-TenantId", tenantIdentifier)
											  .header("Content-Type", "application/json")
											  .header("Authorization", "Basic " + authenticationKey)
											  .get(ClientResponse.class);	
					if (response.getStatus() != 200) {
						throw new RuntimeException("Failed : HTTP error code : "
								+ response.getStatus());
					}
					
					String officeDetailsByOfficeId = response	.getEntity(String.class);
					String[] officeData = officeDetailsByOfficeId.split(",");
					
					
					if(officeData !=null && officeData.length > 0){
						for(int i=0; i<= officeData.length ; i++){
								if(officeData[i].contains("externalId")){
									String[] office = officeData[i].split(":");
							               officeExternalId = office[1].replaceAll("^\"|\"$", "");
							              break; 
								}
							}
						}
					
					
					if(officeExternalId != null || officeExternalId !=""){
						
						clientExternalId = officeExternalId.concat(accountNo);
						
					}
					
					
				}
				
				webResource = client.resource(mifosurl+ "/mifosng-provider/api/v1/search?query=" + clientExternalId+ "&resource=clients");
				ClientResponse response = webResource
						.header("X-mifos-Platform-TenantId", tenantIdentifier)
						.header("Content-Type", "application/json")
						.header("Authorization", "Basic " + authenticationKey)
						.get(ClientResponse.class);				
				if (response.getStatus() != 200) {
					throw new RuntimeException("Failed : HTTP error code : "
							+ response.getStatus());
				}
				String clientDetailsByNationalId = response	.getEntity(String.class);
			  JSONArray clientsData = (JSONArray) JSONValue	.parseWithException(clientDetailsByNationalId);
				if (clientsData != null) {
					if (clientsData.size() > 0) {
					   for (int j = 0; j < clientsData.size(); j++) {
						JSONObject clientData = (JSONObject) clientsData.get(j);
					if (clientData != null && clientData.get("entityType").equals("CLIENT")) {									
					if (clientData.get("entityExternalId").equals(clientExternalId)) {										
							String ClientName = (String) clientData	.get("entityName");
							externalIdSearch = true;
							details = ClientName + "="+ clientData.get("parentId") + "="+ "CMP" + "="+ clientData.get("entityId") + "=" + clientData.get("entityExternalId");										
					 }
				   }
				 }
				}
			  }
			}
			if (!externalIdSearch) {
				String mobileNowithZero = MobileNo;
				client = ClientHelper.createClient();
				webResource = client.resource(mifosurl+ "/mifosng-provider/api/v1/search?query=" + MobileNo+ "&resource=clients");
				ClientResponse clientsDatasearchByMobileNo = webResource
						.header("X-mifos-Platform-TenantId", tenantIdentifier)
						.header("Content-Type", "application/json")
						.header("Authorization", "Basic " + authenticationKey)
						.get(ClientResponse.class);				
							if (clientsDatasearchByMobileNo.getStatus() != 200) {
					throw new RuntimeException("Failed : HTTP error code : "
							+ clientsDatasearchByMobileNo.getStatus());
				}

				String cilentsDataByMobileNo = clientsDatasearchByMobileNo.getEntity(String.class);						
				JSONArray cilentsData = (JSONArray) JSONValue.parseWithException(cilentsDataByMobileNo);						
				if (cilentsData != null) {
				if (cilentsData.size() > 0) {
					for (int k = 0; k < cilentsData.size(); k++) {
					JSONObject clientData = (JSONObject) cilentsData.get(k);									
				if (clientData != null&& clientData.get("entityType").equals("CLIENT")&& clientData.get("entityMobileNo") != null) {
				if (clientData.get("entityMobileNo").equals(MobileNo)|| clientData.get("entityMobileNo").equals(mobileNowithZero)) {					
					String ClientName = (String) clientData.get("entityName");					
					details = ClientName + "="+ clientData.get("parentId") + "="+ "CMP" + "="+ clientData.get("entityId");				
				}			
				else {						
					details = " " + "=" + " " + "=" + "UNMP"+ "=" + " " + "=" + " " + "=" + " ";
				}}
				else if (clientData != null) {	
					details = " " + "=" + " " + "=" + "UNMP" + "="+ " " + "=" + " ";
				}}
				}else {
					details = " " + "=" + " " + "=" + "UNMP" + "=" + " " + "=" + " ";
				}	
				}
			  }					
		} catch (Exception e) {
			logger.error("Exception " + e);
		}
		return details;
	}								
	@Override
	public Page<Mpesa> retriveUnmappedTransactions(Long officeId, Integer offset, Integer limit) {
		Page<Mpesa> unmappedTransactionList = null;
		try {
			int pageNo = (offset == null) ? offset = 0 : Integer.divideUnsigned(offset, 15);
			limit = (limit == null || limit == 0 ) ? limit = Integer.valueOf(15) : limit;
			PageRequest pageable = new PageRequest(pageNo, limit);
			unmappedTransactionList = this.mpesaBridgeRepository.retriveUnmappedTransactions(officeId, pageable);					
		} catch (Exception e) {
			logger.error("Exception while retriveUnmappedTransactions " + e);
		}
		return unmappedTransactionList;
	}

	@Override
	public List<Mpesa> Payment(Long Id) {
		final List<Mpesa> mpesaList = this.mpesaBridgeRepository.retriveTransactionsforPayment(Id);				
		for (Mpesa mpesa : mpesaList) {
			mpesa.setStatus("PAID");
			this.mpesaBridgeRepository.save(mpesa);
		}
		return mpesaList;
	}

	@Override
	public Page<Mpesa> searchMpesaDetail(String status, String mobileNo,
			Date fromDate, Date toDate, Long officeId, Integer offset, Integer limit) {
		Page<Mpesa> TransactionList = null;
		try {
			int pageNo = (offset == null) ? offset = 0 : Integer.divideUnsigned(offset, 15);
			limit = (limit == null || limit == 0 ) ? limit = Integer.valueOf(15) : limit;
			PageRequest pageable = new PageRequest(pageNo, limit);
			if (mobileNo.equals("") && status.equals("")) {
				TransactionList = this.mpesaBridgeRepository.LikeSearch(
						fromDate, toDate, officeId, pageable);			}

			ArrayList<Long> officeIdList = new ArrayList<Long>();
			officeIdList.add(new Long(0));
			if(officeId.longValue() != 0){
				officeIdList.add(officeId);
			}
			if (mobileNo.equals("") && status != null && status != "") {
				if (status.equals("UNMP")) {
					TransactionList = this.mpesaBridgeRepository.unmappedofficed(status, fromDate, toDate, officeIdList, pageable);
				} else {
					TransactionList = this.mpesaBridgeRepository.search(status,fromDate, toDate, officeId, pageable);							
				}
			}
			if (mobileNo != null && mobileNo != "" && status.equals("")&& status == "") {			
				TransactionList = this.mpesaBridgeRepository.likesearch(mobileNo, fromDate, toDate, officeId, pageable);
			}

			if (status != null && status != "" && mobileNo != null && mobileNo != "") {
				if (status.equals("UNMP")) {
					TransactionList = this.mpesaBridgeRepository.UnMappedOffice(status, mobileNo, fromDate, toDate,officeId, pageable);

				} else {
					TransactionList = this.mpesaBridgeRepository.Exactsearch(status, mobileNo, fromDate, toDate, officeId, pageable);
				}
			}

		} catch (Exception e) {
			logger.error("Exception while fetchTransactionByStatus " + e);
		}
		return TransactionList;
	}

}
