package smartcontract.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.tx.ManagedTransaction;
import rx.Subscriber;
import smartcontract.app.generated.SmartContract;
import trading.ContractLoader;

import java.math.BigInteger;

public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        new Application().run();
    }

    private void run() throws Exception {
        ContractLoader contractLoader = new ContractLoader("password",
                "/home/peter/Documents/energy-p2p/private-testnet/keystore/UTC--2018-04-04T09-17-25.118212336Z--9b538e4a5eba8ac0f83d6025cbbabdbd13a32bfe");

        log.info("Deploying smart contract (remember to start mining!!!)");
//        SmartContract contract = contractLoader.deployContract();
//        System.exit(0);


        SmartContract contract = contractLoader.loadContract();
        log.info("Contract is valid: " + contract.isValid());
        String contractAddress = contract.getContractAddress();
        log.info("Smart contract deployed to address " + contractAddress);

//     event BidAccepted(uint indexed roundId, uint indexed contractId, address indexed bidder, uint quantity, uint price, uint time);
        log.info("Value stored in remote smart contract: " + contract.addContract(
                new BigInteger("1", 10),
                new BigInteger("1", 10),
                "0x521892450a22dc762198f6ce597cfc6d85f673a3",
                new BigInteger("10", 10),
                new BigInteger("10", 10)
        ).send());

        Subscriber<SmartContract.BidAcceptedEventResponse> subscriber = new BuyersSubscriber();

        contract.bidAcceptedEventObservable(
                DefaultBlockParameterName.fromString(DefaultBlockParameterName.EARLIEST.getValue()),
                DefaultBlockParameterName.fromString(DefaultBlockParameterName.LATEST.getValue())).subscribe(subscriber);


//        ContractLoader contractLoader = new ContractLoader("password",
//                "/home/peter/Documents/energy-p2p/private-testnet/keystore/UTC--2018-04-04T09-17-25.118212336Z--9b538e4a5eba8ac0f83d6025cbbabdbd13a32bfe");
//        SmartContract smartContract = contractLoader.loadContract();
//        log.info("latestRoundId: " + contractLoader.getLatestRoundId(smartContract).toString());

    }

}
