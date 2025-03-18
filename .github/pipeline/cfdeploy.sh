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

# Step 3: Verify and Checkout Deploy Branch
pwd
cd sdm
echo "Checking deploy branch..."
#git stash
git fetch origin || error_exit "Failed to fetch repository"

if git rev-parse --verify origin/develop_deploy; then
  git checkout develop_deploy || error_exit "Failed to checkout develop_deploy"
else
  error_exit "Branch 'develop_deploy' not found. Please verify the branch name."
fi

# Step 4: Delete the sdm directory for a fresh build
echo "Deleting sdm directory for fresh build..."
rm -rf ~/.m2/repository/com/sap/cds || error_exit "Failed to delete directory"

# Step 5: Set REPOSITORY_ID
echo "Setting REPOSITORY_ID..."
#CF_SPACE="your_cf_space"  # Set your CF space or retrieve from input
#REPOSITORY_ID=""

if [ "$CF_SPACE" = "developcap" ]; then
  REPOSITORY_ID="${REPOSITORY_ID:-your_secret_repository_id}"
else
  if [ -z "$REPOSITORY_ID" ]; then
    error_exit "REPOSITORY_ID must be provided for non-developcap spaces"
  else
    echo "Using provided REPOSITORY_ID"
  fi
fi

# Step 6: Prepare and Deploy to Cloud Foundry
echo "Preparing and Deploying to Cloud Foundry..."
BRANCH_NAME=$(git branch --show-current)
echo "Current Branch: $BRANCH_NAME"
cd /root/workspace/CAP_JAVA_DEPLOY/sdm/cap-notebook/demoapp || error_exit "Failed to change to demoapp directory"

# Replace placeholder with actual REPOSITORY_ID value
sed -i "s|__REPOSITORY_ID__|${REPOSITORY_ID}|g" ./mta.yaml || error_exit "Failed to replace REPOSITORY_ID in mta.yaml"

# mbt build || error_exit "Failed to build using mbt"

# # Install cf CLI plugin
# cf install-plugin multiapps -f || error_exit "Failed to install multiapps plugin"

# # Login to Cloud Foundry


# cf login -a "$CF_API" -u "$CF_USER" -p "$CF_PASSWORD" -o "$CF_ORG" -s "$CF_SPACE" || error_exit "Failed to login to Cloud Foundry"

# # Deploy the application
# echo "Running cf deploy..."
# cf deploy mta_archives/demoappjava_1.0.0.mtar -f || error_exit "Failed to deploy application"
