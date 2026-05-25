#!/usr/bin/env bash
set -euo pipefail

########################################
# CONFIGURATION
########################################
JAVA_VERSION="${JAVA_VERSION:-21}"
MAVEN_VERSION="${MAVEN_VERSION:-3.6.3}"

# These MUST come from Jenkins
ARTIFACTORY_URL="${ARTIFACTORY_URL:?ARTIFACTORY_URL is required}"
CAP_DEPLOYMENT_USER="${CAP_DEPLOYMENT_USER:?CAP_DEPLOYMENT_USER is required}"
CAP_DEPLOYMENT_PASS="${CAP_DEPLOYMENT_PASS:?CAP_DEPLOYMENT_PASS is required}"

# Detect branch name (works in Jenkins or Git CLI)
GIT_BRANCH="${GIT_BRANCH:-${BRANCH_NAME:-$(git rev-parse --abbrev-ref HEAD)}}"
echo "Running on branch: $GIT_BRANCH"

########################################
# CHECK JAVA & MAVEN
########################################
echo "Checking Java & Maven installations..."
java -version || { echo "❌ Java not found!"; exit 1; }
mvn -v || { echo "❌ Maven not found!"; exit 1; }

########################################
# READ CURRENT VERSION
########################################
echo "Reading current Maven project version..."
current_version=$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)
echo "Current version: $current_version"
updated_version="$current_version"

########################################
# BUMP VERSION IF NEEDED
########################################
if [[ "$GIT_BRANCH" == "develop" || "$GIT_BRANCH" == "internal-repo" ]]; then
  if [[ "$current_version" != *-SNAPSHOT ]]; then
    echo "Version lacks -SNAPSHOT; incrementing patch."
    IFS='.' read -r major minor patch <<< "$(echo "$current_version" | tr '-' '.')"
    new_patch=$((patch + 1))
    new_version="${major}.${minor}.${new_patch}-SNAPSHOT"
    sed -i "s|<revision>.*</revision>|<revision>${new_version}</revision>|" pom.xml
    echo "Updated version to $new_version"

    git config user.name "jenkins-bot"
    git config user.email "jenkins@local"
    git add pom.xml
    git commit -m "Increment version to ${new_version}" || echo "No changes to commit"
    git push origin "HEAD:${GIT_BRANCH}"
    updated_version="$new_version"
  else
    echo "Already a -SNAPSHOT version; no bump performed."
  fi
else
  echo "Branch $GIT_BRANCH not eligible for version bump."
fi

########################################
# DEPLOY SNAPSHOT TO ARTIFACTORY
########################################
if [[ "$updated_version" == *-SNAPSHOT ]]; then
  echo "Deploying ${updated_version} to Artifactory..."
  mvn -B -ntp -fae \
    -Dmaven.install.skip=true \
    -Dmaven.test.skip=true \
    -DdeployAtEnd=true \
    -DaltDeploymentRepository="artifactory::default::${ARTIFACTORY_URL}" \
    -Dusername="${CAP_DEPLOYMENT_USER}" \
    -Dpassword="${CAP_DEPLOYMENT_PASS}" \
    deploy
else
  echo "Skipping deploy — not a SNAPSHOT version."
fi

########################################
# VERIFY ARTIFACT IN ARTIFACTORY
########################################
if [[ "$updated_version" == *-SNAPSHOT ]]; then
  group_path="com/sap/cds/sdm"
  metadata_url="${ARTIFACTORY_URL}/${group_path}/${updated_version}/maven-metadata.xml"
  echo "Verifying artifact metadata at: $metadata_url"

  curl -u "${CAP_DEPLOYMENT_USER}:${CAP_DEPLOYMENT_PASS}" -f -I "$metadata_url" \
    || { echo "❌ Metadata not found at $metadata_url"; exit 1; }

  echo "✅ Artifact metadata accessible for $updated_version"
else
  echo "Skipping verification — not a SNAPSHOT version."
fi

########################################
# SUMMARY
########################################
echo "----------------------------------------"
echo "📦 Revision: $current_version"
echo "📦 Final version: $updated_version"
echo "📤 Deployment target: $ARTIFACTORY_URL"
echo "----------------------------------------"
