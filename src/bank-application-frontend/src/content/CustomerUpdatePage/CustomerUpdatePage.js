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
} from '@carbon/react';

const CustomerUpdatePage = () => {
  const [isModalOpened, setModalOpened] = useState(false);
  const [isFailureModalOpened, setIsFailureModalOpened] = useState(false);
  const [isFailureNetworkModalOpened, setIsFailureNetworkModalOpened] = useState(false);
  const [isLoadingModalOpened, setIsLoadingModalOpened] = useState(false);
  const [resultText, setResultText] = useState("");
  const [failureText, setFailureText] = useState("");

  const [enteredCustomerNumber, setEnteredCustomerNumber] = useState('');
  const [enteredCustomerName, setEnteredCustomerName] = useState('');
  const [enteredCustomerAddress, setEnteredCustomerAddress] = useState('');
  const [enteredDateOfBirth, setEnteredDateOfBirth] = useState('');
  const [enteredCreditScore, setEnteredCreditScore] = useState('');
  const [enteredReviewDate, setEnteredReviewDate] = useState('');

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

  async function updateCustomer() {
    try {
      await axios
        .post(process.env.REACT_APP_CUSTOMERSERVICES_URL + '/updatecust', null, {
          params: {
            custNumber: enteredCustomerNumber,
            custName: enteredCustomerName,
            custAddress: enteredCustomerAddress,
            custDoB: enteredDateOfBirth,
            custCreditScore: enteredCreditScore ? parseInt(enteredCreditScore) : 0,
            custReviewDate: enteredReviewDate,
          }
        })
        .then((response) => {
          let responseData = response.data;
          if (responseData.success) {
            setResultText(responseData.smallText || "Customer updated successfully");
            displayLoadingModal();
            displayModal();
          } else {
            setFailureText(responseData.smallText || "Customer update failed");
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
      console.log("Error in customer update: " + e);
      displayLoadingModal();
      displayFailedModal();
    }
  }

  async function submitButtonHandler() {
    displayLoadingModal();
    updateCustomer();
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
          <BreadcrumbItem>Update Customer</BreadcrumbItem>
        </Breadcrumb>
        <h1 className="landing-page__heading">Update Customer</h1>
      </Column>
      <div className="content-parent">
        <div className="left-content-account">
          <Form>
            <Stack gap={7}>
              <div style={{ width: 500 }}>
                <TextInput
                  id="customer-number"
                  type="text"
                  labelText="Customer Number"
                  placeholder="Enter customer number"
                  value={enteredCustomerNumber}
                  onChange={(e) => setEnteredCustomerNumber(e.target.value)}
                />
              </div>
              <div style={{ width: 500 }}>
                <TextInput
                  id="customer-name"
                  type="text"
                  labelText="Customer Name"
                  placeholder="Enter customer name"
                  value={enteredCustomerName}
                  onChange={(e) => setEnteredCustomerName(e.target.value)}
                />
              </div>
              <div style={{ width: 500 }}>
                <TextInput
                  id="customer-address"
                  type="text"
                  labelText="Customer Address"
                  placeholder="Enter customer address"
                  value={enteredCustomerAddress}
                  onChange={(e) => setEnteredCustomerAddress(e.target.value)}
                />
              </div>
              <div style={{ width: 500 }}>
                <TextInput
                  id="date-of-birth"
                  type="text"
                  labelText="Date of Birth (yyyy-mm-dd)"
                  placeholder="e.g. 1990-01-15"
                  value={enteredDateOfBirth}
                  onChange={(e) => setEnteredDateOfBirth(e.target.value)}
                />
              </div>
              <div style={{ width: 500 }}>
                <TextInput
                  id="credit-score"
                  type="text"
                  labelText="Credit Score"
                  placeholder="Enter credit score"
                  value={enteredCreditScore}
                  onChange={(e) => setEnteredCreditScore(e.target.value)}
                />
              </div>
              <div style={{ width: 500 }}>
                <TextInput
                  id="review-date"
                  type="text"
                  labelText="Review Date (yyyy-mm-dd)"
                  placeholder="e.g. 2025-06-01"
                  value={enteredReviewDate}
                  onChange={(e) => setEnteredReviewDate(e.target.value)}
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
            <h5>Customer updated successfully</h5>
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
            <h4>Updating customer...</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureNetworkModalOpened}
            preventCloseOnClickOutside
            onRequestClose={displayFailedNetworkModal}>
            <h4>Customer update failed due to a network error</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureModalOpened}
            onRequestClose={displayFailedModal}
            preventCloseOnClickOutside>
            <h5>Customer update failed</h5>
            <br />
            <br />
            <p>{failureText || "Please check that all inputs are valid."}</p>
          </Modal>
        </div>
        <div className="right-content-account">
          <img className="right-content-account"
            src={`${process.env.PUBLIC_URL}/ibm-db2-support-leadspace.png`}
            alt="customer update"
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

export default CustomerUpdatePage;
