package org.cheetah.lsf;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.cheetah.lsf.exceptions.StepFunctionNameIsMissing;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

public class CheetahLambdaStepfunctionsApplication implements RequestHandler<Map<String,Object>, String> {
	private static final String SF_INPUT_PARAMS = "SF_INPUT_PARAMS";
	private static final String SF_NAME = "SF_NAME";
	private static final String EXEC_ON_WORKING_DAY_NUMBER = "EXEC_ON_WORKING_DAY_NUMBER";
	final String ACCESS_KEY = "test";
	final String SECRET_KEY = "test";

	@Override
	public String handleRequest(Map<String,Object> event, Context context) {
		Object sfParams = event.containsKey(SF_INPUT_PARAMS) ? event.get(SF_INPUT_PARAMS) : "{}";
		String sfName = event.containsKey(SF_NAME) ? (String) event.get(SF_NAME) : null;
		if (sfName == null) {
			throw new StepFunctionNameIsMissing("SF_NAME attribute is missing in input data");
		}

		Set<String> keys = event.keySet();
		System.out.println("Params in input: ");
		for (String key : keys) {
			System.out.println("\t" + key + ": " + event.get(key));
		}

		LambdaLogger log = context.getLogger();
		String bucketName = "vgi-step-functions-scheduler";
		String key = "italy_not_working_day.xlsx";

		log.log("Tentativo di connessione al bucket " + bucketName);
		S3Client s3 = S3Client.builder().endpointOverride(URI.create("https://s3.localhost.localstack.cloud:4566"))
				.credentialsProvider(
						StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
				.region(Region.US_EAST_1).build();
		log.log("Connesso al bucket " + bucketName);
		log.log("Prendo il file " + key);
		GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
		log.log("File " + key + " preso");

//		s3.getObject(getObjectRequest, Paths.get(localFilePath));

		Set<LocalDate> holidays = new TreeSet<>();

		try (ResponseInputStream<GetObjectResponse> response = s3.getObject(getObjectRequest);
				Workbook workbook = new XSSFWorkbook(response)) {
			Sheet sheet = workbook.getSheetAt(0);
			Iterator<Row> rowIterator = sheet.iterator();

			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				if (row.getRowNum() == 0) {
					continue; // Skip header row
				}
				int day = (int) row.getCell(0).getNumericCellValue();
				int month = (int) row.getCell(1).getNumericCellValue();
				LocalDate holiday = LocalDate.of(LocalDate.now().getYear(), month, day);
				holidays.add(holiday);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		LocalDate today = LocalDate.now();
		int workdayNumber = calculateWorkdayNumber(today, holidays);
		int execOnWorkingDayNumber = 1;
		if (event.containsKey(EXEC_ON_WORKING_DAY_NUMBER)) {
			execOnWorkingDayNumber = Integer.parseInt((String) event.get(EXEC_ON_WORKING_DAY_NUMBER));
		}
		String result = "Today is " + workdayNumber + "° working day of the month.";
		System.out.println("************************");
		System.out.println("************************");
		System.out.println("************************");
		System.out.println("Giorno lavorativo: " + workdayNumber + "°");
		System.out.println("Da eseguire il: " + execOnWorkingDayNumber + "°");
		System.out.println("************************");
		System.out.println("************************");
		System.out.println("************************");

		if (execOnWorkingDayNumber == workdayNumber) {
			// eseguo la step function
			System.out.println("Eseguo step function " + sfName + " con i seguenti parametri: " + sfParams);
			SfnClient sfnClient = SfnClient.builder().endpointOverride(URI.create("https://localhost.localstack.cloud:4566"))
	                .region(Region.US_EAST_1)
	                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY,SECRET_KEY)))
	                .build();
			Map<String,Object> inputParams = (Map<String, Object>) event.get(SF_INPUT_PARAMS);
			ObjectMapper objectMapper = new ObjectMapper();
			String input = "{}";
			try {
				input =  objectMapper.writeValueAsString(inputParams);
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			StartExecutionRequest startExecutionRequest = StartExecutionRequest.builder()
                    .stateMachineArn(sfName) 
                    .input(input)
                    .build();
			StartExecutionResponse startExecutionResponse = sfnClient.startExecution(startExecutionRequest);

            // Stampa l'ID dell'esecuzione
            context.getLogger().log("Execution ARN: " + startExecutionResponse.executionArn());
		}
		return result;
	}

	private int calculateWorkdayNumber(LocalDate date, Set<LocalDate> holidays) {
		LocalDate startDate = date.withDayOfMonth(1);
		int workdayCount = 0;
		System.out.println("Holidays: *****");
		System.out.println("\t" + holidays);
		for (LocalDate currentDate = startDate; !currentDate.isAfter(date); currentDate = currentDate.plusDays(1)) {
			if (currentDate.getDayOfWeek().getValue() < 6 && !holidays.contains(currentDate)) {
				workdayCount++;
			}
		}

		return workdayCount;
	}

	// TODO Auto-generated method stub

//	@Override
//	public void run(String... args) throws Exception {
//		String excelFilePath = "/Users/edoardo/temp/festività_italia.xlsx";
//		
//		
//		
//		Set<LocalDate> holidays = new HashSet<>();
//
//		try (FileInputStream fis = new FileInputStream(new File(excelFilePath));
//				Workbook workbook = new XSSFWorkbook(fis)) {
//			Sheet sheet = workbook.getSheetAt(0);
//			Iterator<Row> rowIterator = sheet.iterator();
//
//			while (rowIterator.hasNext()) {
//				Row row = rowIterator.next();
//				if (row.getRowNum() == 0) {
//					continue; // Skip header row
//				}
//				int day = (int) row.getCell(0).getNumericCellValue();
//				int month = (int) row.getCell(1).getNumericCellValue();
//				LocalDate holiday = LocalDate.of(LocalDate.now().getYear(), month, day);
//				holidays.add(holiday);
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//		LocalDate today = LocalDate.now();
//		int workdayNumber = calculateWorkdayNumber(today, holidays);
//		System.out.println("Oggi è il " + workdayNumber + "° giorno lavorativo del mese.");
//	}
//
//	private int calculateWorkdayNumber(LocalDate date, Set<LocalDate> holidays) {
//		LocalDate startDate = date.withDayOfMonth(1);
//		int workdayCount = 0;
//
//		for (LocalDate currentDate = startDate; !currentDate.isAfter(date); currentDate = currentDate.plusDays(1)) {
//			if (currentDate.getDayOfWeek().getValue() < 6 && !holidays.contains(currentDate)) {
//				workdayCount++;
//			}
//		}
//
//		return workdayCount;
//	}

}
