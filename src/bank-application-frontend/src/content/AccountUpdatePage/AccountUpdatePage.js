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
  Dropdown,
} from '@carbon/react';

const AccountUpdatePage = () => {
  const [isModalOpened, setModalOpened] = useState(false);
  const [isFailureModalOpened, setIsFailureModalOpened] = useState(false);
  const [isFailureNetworkModalOpened, setIsFailureNetworkModalOpened] = useState(false);
  const [isLoadingModalOpened, setIsLoadingModalOpened] = useState(false);
  const [resultText, setResultText] = useState("");
  const [failureText, setFailureText] = useState("");

  const [enteredAccountNumber, setEnteredAccountNumber] = useState('');
  const [enteredAccountType, setEnteredAccountType] = useState('');
  const [enteredInterestRate, setEnteredInterestRate] = useState('');
  const [enteredOverdraft, setEnteredOverdraft] = useState('');

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

  async function updateAccount() {
    try {
      await axios
        .post(process.env.REACT_APP_CUSTOMERSERVICES_URL + '/updateacc', null, {
          params: {
            acctNumber: enteredAccountNumber,
            acctType: enteredAccountType,
            acctInterestRate: enteredInterestRate,
            acctOverdraft: enteredOverdraft,
          }
        })
        .then((response) => {
          let responseData = response.data;
          if (responseData.success) {
            setResultText(responseData.smallText || "Account updated successfully");
            displayLoadingModal();
            displayModal();
          } else {
            setFailureText(responseData.smallText || "Account update failed");
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
      console.log("Error in account update: " + e);
      displayLoadingModal();
      displayFailedModal();
    }
  }

  async function submitButtonHandler() {
    displayLoadingModal();
    updateAccount();
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
          <BreadcrumbItem>Update Account</BreadcrumbItem>
        </Breadcrumb>
        <h1 className="landing-page__heading">Update Account</h1>
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
                <Dropdown
                  id="account-type"
                  titleText="Account Type"
                  label="Account Type"
                  items={["MORTGAGE", "ISA", "LOAN", "SAVING", "CURRENT"]}
                  onChange={({ selectedItem }) =>
                    setEnteredAccountType(selectedItem)
                  }
                  selectedItem={enteredAccountType}
                />
              </div>

              <div style={{ width: 500 }}>
                <TextInput
                  id="interest-rate"
                  type="text"
                  labelText="Interest Rate"
                  placeholder="Enter interest rate"
                  value={enteredInterestRate}
                  onChange={(e) => setEnteredInterestRate(e.target.value)}
                />
              </div>

              <div style={{ width: 500 }}>
                <TextInput
                  id="overdraft"
                  type="text"
                  labelText="Overdraft Limit"
                  placeholder="Enter overdraft limit"
                  value={enteredOverdraft}
                  onChange={(e) => setEnteredOverdraft(e.target.value)}
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
            <h5>Account updated successfully</h5>
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
            <h4>Updating account...</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureNetworkModalOpened}
            preventCloseOnClickOutside
            onRequestClose={displayFailedNetworkModal}>
            <h4>Account update failed due to a network error</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureModalOpened}
            onRequestClose={displayFailedModal}
            preventCloseOnClickOutside>
            <h5>Account update failed</h5>
            <br />
            <br />
            <p>{failureText || "Please check that all inputs are valid."}</p>
          </Modal>
        </div>
        <div className="right-content-account">
          <img className="right-content-account"
            src={`${process.env.PUBLIC_URL}/ibm-db2-support-leadspace.png`}
            alt="account update"
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

export default AccountUpdatePage;
