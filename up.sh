mvn21 clean install -DskipTests

awslocal cloudformation deploy  --stack-name vgi-lambda-bucket-stack --template-file "./src/main/cloud/templates/s3_vgi_lambda_repo.yml" --profile localstack --region us-east-1 

awslocal s3 cp target/lambda-SF-1.0.0-SNAPSHOT.jar s3://vgi-lambda-repo/lambda-SF-1.0.0-SNAPSHOT.jar

awslocal cloudformation deploy  --stack-name vgi-lambda-stack --template-file "/Users/edoardo/Documents/workspaces/joinup/cheetah-lambda-stepfunctions/src/main/cloud/templates/lamda_create.yml" --profile localstack --region us-east-1 --capabilities CAPABILITY_NAMED_IAM

awslocal s3 cp  src/main/cloud/italy_not_working_day.xlsx  s3://vgi-step-functions-scheduler/italy_not_working_day.xlsx
