/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React from 'react';
import { useState } from 'react';
import axios from 'axios';
import {
  Breadcrumb,
  BreadcrumbItem,
  Modal,
  Grid,
  Column,
  Button,
  Form,
  Stack,
  TextInput,
  RadioButtonGroup,
  RadioButton,
} from '@carbon/react';

const PaymentPage = () => {
  const [isModalOpened, setModalOpened] = useState(false);
  const [isFailureModalOpened, setIsFailureModalOpened] = useState(false);
  const [isFailureNetworkModalOpened, setIsFailureNetworkModalOpened] = useState(false);
  const [isLoadingModalOpened, setIsLoadingModalOpened] = useState(false);
  const [resultText, setResultText] = useState("");

  const [enteredAccountNumber, setEnteredAccountNumber] = useState('');
  const [enteredAmount, setEnteredAmount] = useState('');
  const [enteredOrganisation, setEnteredOrganisation] = useState('');
  const [paymentType, setPaymentType] = useState('Debit');

  function displayModal() {
    setModalOpened(wasOpened => !wasOpened);
  }

  function displayLoadingModal() {
    setIsLoadingModalOpened(wasOpened => !wasOpened);
  }

  function displayFailedModal() {
    setIsFailureModalOpened(wasOpened => !wasOpened);
  }

  function displayFailedNetworkModal() {
    setIsFailureNetworkModalOpened(wasOpened => !wasOpened);
  }

  async function makePayment() {
    try {
      await axios
        .post(process.env.REACT_APP_PAYMENT_URL + '/paydbcr', null, {
          params: {
            acctNumber: enteredAccountNumber,
            debit: paymentType === 'Debit',
            amount: parseFloat(enteredAmount),
            organisation: enteredOrganisation,
          }
        })
        .then((response) => {
          let responseData = response.data;
          if (responseData.success) {
            setResultText(responseData.smallText || "Payment processed successfully");
            displayLoadingModal();
            displayModal();
          } else {
            setResultText(responseData.smallText || "Payment failed");
            displayLoadingModal();
            displayFailedModal();
          }
        })
        .catch(function (error) {
          if (error.response) {
            displayLoadingModal();
            displayFailedModal();
            console.log(error);
          } else if (error.request) {
            displayLoadingModal();
            displayFailedNetworkModal();
            console.log(error);
          }
        });
    } catch (e) {
      console.log("Error in payment: " + e);
      displayLoadingModal();
      displayFailedModal();
    }
  }

  async function submitButtonHandler() {
    displayLoadingModal();
    makePayment();
  }

  return (
    <Grid className="landing-page" fullWidth>
      <Column lg={16} md={8} sm={4} className="landing-page__banner">
        <Breadcrumb noTrailingSlash aria-label="Page navigation">
          <BreadcrumbItem>
            <a href="./">Home</a>
          </BreadcrumbItem>
          <BreadcrumbItem>
            <a href="./#/profile/Admin">Control Panel</a>
          </BreadcrumbItem>
          <BreadcrumbItem>Make Payment</BreadcrumbItem>
        </Breadcrumb>
        <h1 className="landing-page__heading">Make a Payment</h1>
      </Column>
      <div className="content-parent">
        <div className="left-content-account">
          <Form>
            <Stack gap={7}>
              <div style={{ width: 500 }}>
                <TextInput
                  id="account-number"
                  type="text"
                  labelText="Account Number"
                  placeholder="Enter account number"
                  value={enteredAccountNumber}
                  onChange={(e) => setEnteredAccountNumber(e.target.value)}
                />
              </div>

              <div style={{ width: 500 }}>
                <RadioButtonGroup
                  legendText="Payment Type"
                  name="payment-type"
                  defaultSelected="Debit"
                  onChange={(value) => setPaymentType(value)}
                >
                  <RadioButton labelText="Debit" value="Debit" id="radio-debit" />
                  <RadioButton labelText="Credit" value="Credit" id="radio-credit" />
                </RadioButtonGroup>
              </div>

              <div style={{ width: 500 }}>
                <TextInput
                  id="amount"
                  type="text"
                  labelText="Amount"
                  placeholder="Enter amount"
                  value={enteredAmount}
                  onChange={(e) => setEnteredAmount(e.target.value)}
                />
              </div>

              <div style={{ width: 500 }}>
                <TextInput
                  id="organisation"
                  type="text"
                  labelText="Organisation Name"
                  placeholder="Enter organisation name"
                  value={enteredOrganisation}
                  onChange={(e) => setEnteredOrganisation(e.target.value)}
                />
              </div>

              <Button className="displayModal" onClick={submitButtonHandler}>
                Submit
              </Button>
            </Stack>
          </Form>
          <Modal
            passiveModal
            size="sm"
            open={isModalOpened}
            onRequestClose={displayModal}
            preventCloseOnClickOutside>
            <h5>Payment Successful</h5>
            <br />
            <br />
            <p>{resultText}</p>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isLoadingModalOpened}
            preventCloseOnClickOutside
            onRequestClose={displayLoadingModal}>
            <h4>Processing payment...</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureNetworkModalOpened}
            preventCloseOnClickOutside
            onRequestClose={displayFailedNetworkModal}>
            <h4>Payment failed due to a network error</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureModalOpened}
            onRequestClose={displayFailedModal}
            preventCloseOnClickOutside>
            <h5>Payment failed</h5>
            <br />
            <br />
            <p>{resultText || "Please check that all inputs are valid."}</p>
          </Modal>
        </div>
        <div className="right-content-account">
          <img className="right-content-account"
            src={`${process.env.PUBLIC_URL}/ibm-db2-support-leadspace.png`}
            alt="payment"
          />
        </div>
      </div>
      <Column
        lg={16}
        md={8}
        sm={4}
        className="landing-page__r3 bottom-Column"
      />
    </Grid>
  );
};

export default PaymentPage;
