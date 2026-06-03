package creditscoring;

import com.intuit.karate.junit5.Karate;

class CreditScoringTest {

    @Karate.Test
    Karate testCreditScoringLiberty() {
        return Karate.run("credit-scoring-liberty").relativeTo(getClass());
    }

    @Karate.Test
    Karate testCreditScoringZosConnect() {
        return Karate.run("credit-scoring-zosconnect").relativeTo(getClass());
    }
}
