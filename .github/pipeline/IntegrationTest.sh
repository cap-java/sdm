#!/bin/bash

# Display an error and exit
error_exit() {
  echo "$1" 1>&2
  exit 1
}

# Step 1: Checkout repository
#echo "Checking out repository..."
#git clone -b ${BRANCH} https://github.com/cap-java/sdm.git || error_exit "Failed to clone repository"
#cd sdm || error_exit "Failed to change directory"

# Step 2: Set up Java 17
# This step assumes Java 17 is already installed. Verify version.
#echo "Setting up Java 17..."
#java -version || error_exit "Java 17 is not set up properly"

if [ "$CF_SPACE" == "developcap" ]; then
  CF_SPACE="developcap"  # Replace with secret retrieval
fi
echo "Space determined: $CF_SPACE"

cf login -a "$CF_API" -u "$CF_USER" -p "$CF_PASSWORD" -o "$CF_ORG" -s "$CF_SPACE" || error_exit "Failed to login to Cloud Foundry"

# Step 6: Fetch and Escape Client Details
echo "Fetching and escaping client details..."
service_instance_guid=$(cf service demoappjava-public-uaa --guid)
[ -z "$service_instance_guid" ] && error_exit "Error: Unable to retrieve service instance GUID"

bindings_response=$(cf curl "/v3/service_credential_bindings?service_instance_guids=${service_instance_guid}")
binding_guid=$(echo $bindings_response | jq -r '.resources[0].guid')
[ -z "$binding_guid" ] && error_exit "Error: Unable to retrieve binding GUID"

binding_details=$(cf curl "/v3/service_credential_bindings/${binding_guid}/details")
clientSecret=$(echo "$binding_details" | jq -r '.credentials.clientsecret')
[ -z "$clientSecret" ] || [ "$clientSecret" == "null" ] && error_exit "Error: clientSecret is not set or is null"
escapedClientSecret=$(echo "$clientSecret" | sed 's/\$/\\$/g')

clientID=$(echo "$binding_details" | jq -r '.credentials.clientid')
[ -z "$clientID" ] || [ "$clientID" == "null" ] && error_exit "Error: clientID is not set or is null"

cd /root/workspace/CAP_JAVA_INTEGRATION_TEST/sdm
pwd

# Step 7: Run integration tests
echo "Running integration tests..."
PROPERTIES_FILE="sdm/src/test/resources/credentials.properties"
appUrl="${CF_ORG}-${CF_SPACE}-demoappjava-srv.cfapps.eu12.hana.ondemand.com"
authUrl="https://sdmgoogleworkspace.authentication.eu12.hana.ondemand.com"  

cat > "$PROPERTIES_FILE" <<EOL
appUrl=$appUrl
authUrl=$authUrl
clientID=$clientID
clientSecret=$escapedClientSecret
username=$CF_USER
password=$CF_PASSWORD
EOL

mvn clean verify -P integration-tests -DskipUnitTests || error_exit "Maven tests failed"
