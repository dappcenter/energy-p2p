package trading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.tx.ManagedTransaction;
import rx.Subscriber;
import smartcontract.app.generated.SmartContract;

import java.io.IOException;

class ContractLoader {
    public static final String CONTRACT_ADDRESS = "0xB08a4Aa7904d50155d10B8cE447Cc4b3fae212A4";
    private Web3j web3j;
    private Credentials credentials;
    private static final Logger log = LoggerFactory.getLogger(ContractLoader.class);

    public Web3j getWeb3j() {
        return web3j;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public ContractLoader(String password, String walletFilePath) {
        web3j = Web3j.build(new HttpService(
                "http://localhost:8110"));
        try {
            log.info("Connected to Ethereum client version: "
                    + web3j.web3ClientVersion().send().getWeb3ClientVersion());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            credentials = WalletUtils.loadCredentials(password, walletFilePath);
            log.info("Credentials loaded");
        } catch (IOException | CipherException e) {
            e.printStackTrace();
        }
    }

    public SmartContract loadContractWithSubscriber(Subscriber<SmartContract.BidAcceptedEventResponse> subscriber) {
        SmartContract contract = SmartContract.load(
                CONTRACT_ADDRESS, web3j, credentials, ManagedTransaction.GAS_PRICE, Contract.GAS_LIMIT);
        try {
            log.info("Contract is valid: " + contract.isValid());
        } catch (IOException e) {
            e.printStackTrace();
        }

        contract.bidAcceptedEventObservable(
                DefaultBlockParameterName.fromString(DefaultBlockParameterName.EARLIEST.getValue()),
                DefaultBlockParameterName.fromString(DefaultBlockParameterName.LATEST.getValue())).subscribe(subscriber);

        return contract;
    }
}