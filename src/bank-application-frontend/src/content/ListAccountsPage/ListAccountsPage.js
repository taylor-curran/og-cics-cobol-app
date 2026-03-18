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
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
} from '@carbon/react';

const ListAccountsPage = () => {
  const [isTableOpened, setTableOpened] = useState(false);
  const [isFailureModalOpened, setIsFailureModalOpened] = useState(false);
  const [isFailureNetworkModalOpened, setIsFailureNetworkModalOpened] = useState(false);
  const [isLoadingModalOpened, setIsLoadingModalOpened] = useState(false);
  const [resultTitle, setResultTitle] = useState("");
  const [failureText, setFailureText] = useState("");
  const [accountRows, setAccountRows] = useState([]);

  const [enteredCustomerNumber, setEnteredCustomerNumber] = useState('');

  const accountHeaders = [
    { key: 'id', header: 'Account Number' },
    { key: 'accountType', header: 'Account Type' },
    { key: 'availableBalance', header: 'Available Balance' },
    { key: 'actualBalance', header: 'Actual Balance' },
    { key: 'interestRate', header: 'Interest Rate' },
    { key: 'overdraft', header: 'Overdraft' },
  ];

  function displayLoadingModal() {
    setIsLoadingModalOpened(wasOpened => !wasOpened);
  }

  function displayFailedModal() {
    setIsFailureModalOpened(wasOpened => !wasOpened);
  }

  function displayFailedNetworkModal() {
    setIsFailureNetworkModalOpened(wasOpened => !wasOpened);
  }

  async function listAccounts() {
    try {
      await axios
        .post(process.env.REACT_APP_CUSTOMERSERVICES_URL + '/listacc', null, {
          params: {
            custNumber: enteredCustomerNumber,
          }
        })
        .then((response) => {
          let responseData = response.data;
          if (responseData.success) {
            setResultTitle(responseData.largeText);
            let accounts = responseData.accounts || [];
            let rows = accounts.map((acc, index) => ({
              id: String(acc.commAccno || acc.accountNumber || index),
              accountType: acc.commAccType || acc.accountType || '',
              availableBalance: acc.commAvailBal != null ? String(acc.commAvailBal) : (acc.availableBalance != null ? String(acc.availableBalance) : ''),
              actualBalance: acc.commActBal != null ? String(acc.commActBal) : (acc.actualBalance != null ? String(acc.actualBalance) : ''),
              interestRate: acc.commIntRate != null ? String(acc.commIntRate) : (acc.interestRate != null ? String(acc.interestRate) : ''),
              overdraft: acc.commOverdraft != null ? String(acc.commOverdraft) : (acc.overdraft != null ? String(acc.overdraft) : ''),
            }));
            setAccountRows(rows);
            displayLoadingModal();
            setTableOpened(true);
          } else {
            setFailureText(responseData.smallText || "Failed to list accounts");
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
      console.log("Error in list accounts: " + e);
      displayLoadingModal();
      displayFailedModal();
    }
  }

  async function submitButtonHandler() {
    setTableOpened(false);
    displayLoadingModal();
    listAccounts();
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
          <BreadcrumbItem>List Accounts</BreadcrumbItem>
        </Breadcrumb>
        <h1 className="landing-page__heading">List Accounts by Customer</h1>
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
              <Button className="displayModal" onClick={submitButtonHandler}>
                Submit
              </Button>
            </Stack>
          </Form>

          {isTableOpened && (
            <div style={{ marginTop: '2rem' }}>
              <h4>{resultTitle}</h4>
              <br />
              <DataTable rows={accountRows} headers={accountHeaders}>
                {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
                  <Table {...getTableProps()}>
                    <TableHead>
                      <TableRow>
                        {headers.map((header) => (
                          <TableHeader {...getHeaderProps({ header })} key={header.key}>
                            {header.header}
                          </TableHeader>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((row) => (
                        <TableRow {...getRowProps({ row })} key={row.id}>
                          {row.cells.map((cell) => (
                            <TableCell key={cell.id}>{cell.value}</TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </DataTable>
            </div>
          )}

          <Modal
            passiveModal
            size="sm"
            open={isLoadingModalOpened}
            preventCloseOnClickOutside
            onRequestClose={displayLoadingModal}>
            <h4>Loading accounts...</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureNetworkModalOpened}
            preventCloseOnClickOutside
            onRequestClose={displayFailedNetworkModal}>
            <h4>Failed to list accounts due to a network error</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureModalOpened}
            onRequestClose={displayFailedModal}
            preventCloseOnClickOutside>
            <h5>Failed to list accounts</h5>
            <br />
            <br />
            <p>{failureText || "Please check that the customer number is valid."}</p>
          </Modal>
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

export default ListAccountsPage;
