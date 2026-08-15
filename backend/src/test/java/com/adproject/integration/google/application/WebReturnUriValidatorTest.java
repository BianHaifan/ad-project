package com.adproject.integration.google.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class WebReturnUriValidatorTest {

    @Test void acceptsHttpsAbsoluteUri() {
        assertThat(WebReturnUriValidator.parse("https://app.example.com/recruiter/google-oauth"))
                .isEqualTo(URI.create("https://app.example.com/recruiter/google-oauth"));
    }

    @Test void acceptsLoopbackHttpUris() {
        assertThat(WebReturnUriValidator.parse("http://localhost:3000/recruiter/google-oauth")).isNotNull();
        assertThat(WebReturnUriValidator.parse("http://127.0.0.1:3000/recruiter/google-oauth")).isNotNull();
        assertThat(WebReturnUriValidator.parse("http://[::1]:3000/recruiter/google-oauth")).isNotNull();
    }

    @Test void rejectsExternalHttpUri() {
        assertThatThrownBy(() -> WebReturnUriValidator.parse("http://app.example.com/recruiter/google-oauth"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsFragmentAndUserinfo() {
        assertThatThrownBy(() -> WebReturnUriValidator.parse("https://app.example.com/recruiter#frag"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebReturnUriValidator.parse("https://user@app.example.com/recruiter"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsRelativeEmptyHostAndUnsafeScheme() {
        assertThatThrownBy(() -> WebReturnUriValidator.parse("/recruiter/google-oauth"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebReturnUriValidator.parse("https:///recruiter"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebReturnUriValidator.parse("ftp://app.example.com/recruiter"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void blankReturnsNull() {
        assertThat(WebReturnUriValidator.parse(null)).isNull();
        assertThat(WebReturnUriValidator.parse("   ")).isNull();
    }
}
