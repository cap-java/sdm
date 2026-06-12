package my.bookshop.handlers;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Repository {
  private String displayName;
  private String externalId;
  private String description;
  private String repositoryId;
public String getDisplayName() {
    return displayName;
}
public void setDisplayName(String displayName) {
    this.displayName = displayName;
}
public String getExternalId() {
    return externalId;
}
public void setExternalId(String externalId) {
    this.externalId = externalId;
}
public String getDescription() {
    return description;
}
public void setDescription(String description) {
    this.description = description;
}
public String getRepositoryId() {
    return repositoryId;
}
public void setRepositoryId(String repositoryId) {
    this.repositoryId = repositoryId;
}
  
}

