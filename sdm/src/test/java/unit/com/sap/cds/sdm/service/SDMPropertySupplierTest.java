package unit.com.sap.cds.sdm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sap.cds.sdm.service.SDMPropertySupplier;
import com.sap.cds.sdm.service.SDMUser;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationOptions;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SDMPropertySupplierTest {
  @Test
  void userNameIsSetAsAdditionalAttribute() {
    var options =
        ServiceBindingDestinationOptions.forService(mock(ServiceBinding.class))
            .withOption(SDMUser.of("my_ods_user"))
            .build();

    var supplier = new SDMPropertySupplier(options);

    var attributes = supplier.getOAuth2Options().getAdditionalTokenRetrievalParameters();
    assertThat(attributes)
        .containsEntry(
            "authorities",
            "{\"az_attr\":{\"X-EcmUserEnc\":\"my_ods_user\",\"X-EcmAddPrincipals\":\"my_ods_user\"}}");
  }

  @Test
  void specialCharactersInUserNameAreEscaped() {
    var options =
        ServiceBindingDestinationOptions.forService(mock(ServiceBinding.class))
            .withOption(SDMUser.of("my_attack_user\\abc\"def"))
            .build();

    var supplier = new SDMPropertySupplier(options);

    var attributes = supplier.getOAuth2Options().getAdditionalTokenRetrievalParameters();
    assertThat(attributes)
        .containsEntry(
            "authorities",
            "{\"az_attr\":{\"X-EcmUserEnc\":\"my_attack_user\\\\abc\\\"def\",\"X-EcmAddPrincipals\":\"my_attack_user\\\\abc\\\"def\"}}");
  }

  @Test
  void sdmEndpointIsReturnedAsServiceUri() {
    var binding = mock(ServiceBinding.class);
    Map<String, Object> credentials =
        Map.of(
            "endpoints",
            Map.of("ecmservice", Map.of("url", "https://buslog-write-host/", "timeout", 900000)));
    when(binding.getCredentials()).thenReturn(credentials);
    var options = ServiceBindingDestinationOptions.forService(binding).build();

    var supplier = new SDMPropertySupplier(options);

    assertThat(supplier.getServiceUri()).isEqualTo(URI.create("https://buslog-write-host/"));
  }
}
