package zosconnect;

import com.intuit.karate.junit5.Karate;

class ZosConnectTest {

    @Karate.Test
    Karate testAccountOperationsZosConnect() {
        return Karate.run("account-operations-zosconnect").relativeTo(getClass());
    }

    @Karate.Test
    Karate testCustomerOperationsZosConnect() {
        return Karate.run("customer-operations-zosconnect").relativeTo(getClass());
    }

    @Karate.Test
    Karate testPaymentZosConnect() {
        return Karate.run("payment-zosconnect").relativeTo(getClass());
    }
}
