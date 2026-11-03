package com.ai.xbot.service.xaccount;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PkceTest {

    /** RFC 7636 Appendix B test vector. */
    @Test
    void s256ChallengeMatchesRfc7636() {
        assertThat(XAccountService.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void scopesAreMinimal() {
        assertThat(XAccountService.SCOPES).isEqualTo("tweet.read tweet.write users.read offline.access");
    }
}
