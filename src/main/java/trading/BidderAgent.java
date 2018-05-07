package trading;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class BidderAgent extends Agent {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(BidderAgent.class);
    public static final BigInteger ROUND_ID = new BigInteger("1", 10);

    private HashMap<BigInteger, Bid> bidsForRounds = new HashMap<>();
    private DFHelper helper;

    protected void setup() {
        helper = DFHelper.getInstance();

        Object[] args = getArguments();
        if (args != null && args.length == 2) {

//            Subscriber<SmartContract.BidAcceptedEventResponse> subscriber = new BiddersSubscriber();
//            ContractLoader contractLoader = new ContractLoader("password",
//                    "/home/peter/Documents/energy-p2p/private-testnet/keystore/UTC--2018-04-04T09-17-25.118212336Z--9b538e4a5eba8ac0f83d6025cbbabdbd13a32bfe");
//            SmartContract smartContract = contractLoader.loadContractWithSubscriber(subscriber);

            String price = (String) args[0];
            String quantity = (String) args[1];

            if (NumberUtils.isCreatable(price) && NumberUtils.isDigits(quantity)) {
                Bid bid = new Bid(NumberUtils.createBigDecimal(price), NumberUtils.createBigInteger(quantity));

                log.info(getAID().getName() + " has issued a new offer" + bid + ".\n");

                bidsForRounds.put(ROUND_ID, bid);

                ServiceDescription serviceDescription = new ServiceDescription();
                serviceDescription.setType("Bidder");
                serviceDescription.setName(getLocalName());
                helper.register(this, serviceDescription);
            } else {
                log.error("Payment must be a positive decimal number and quantity positive integer");
                log.error("Terminating: " + this.getAID().getName());
                doDelete();
            }
        } else {
            log.error("Two arguments required.");
            log.error("Terminating: " + this.getAID().getName());
            doDelete();
        }

        addBehaviour(new CustomContractNetInitiator(this, null));
    }


    private class CustomContractNetInitiator extends ContractNetInitiator {
        private static final long serialVersionUID = 1L;
        private int globalResponses;

        CustomContractNetInitiator(Agent agent, ACLMessage aclMessage) {
            super(agent, aclMessage);
            globalResponses = 0;
        }

        public Vector<ACLMessage> prepareCfps(ACLMessage message) {
            message = new ACLMessage(ACLMessage.CFP);
            Vector<ACLMessage> messages = new Vector<>();

            AID[] agents = helper.searchDF(getAgent(), "Buyer");

            log.info("The Directory Facilitator found the following agents labeled as \"Buyer\": ");
            for (AID agent : agents) {
                log.info(agent.getName());
                message.addReceiver(new AID(agent.getLocalName(), AID.ISLOCALNAME));
            }

            if (agents.length == 0) {
                log.info("No agents matching the type were found. Terminating: "
                        + getAgent().getAID().getName());
                helper.killAgent(getAgent());
            } else {
                message.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
                message.setReplyByDate(new Date(System.currentTimeMillis() + 10000));

                Bid bid = bidsForRounds.get(ROUND_ID);
                message.setContent(getLocalName() + "|" + bid.getPrice() + "|" + bid.getQuantity());

                messages.addElement(message);
            }

            return messages;
        }

        protected void handlePropose(ACLMessage propose, Vector v) {
            log.info(propose.getSender().getName() + " proposes $" + propose.getContent() + "\".");
        }

        protected void handleRefuse(ACLMessage refuse) {
            globalResponses++;
            log.info(refuse.getSender().getName() + " is not willing to buy any lower.");
            helper.removeReceiverAgent(refuse.getSender(), refuse);
        }

        protected void handleFailure(ACLMessage failure) {
            globalResponses++;
            log.info(failure.getSender().getName() + " failed to reply.");
            helper.removeReceiverAgent(failure.getSender(), failure);
        }

        protected void handleInform(ACLMessage inform) {
            globalResponses++;
            log.info(getAID().getName() + " has no stored power available.");
            helper.killAgent(myAgent);
        }

        protected void handleAllResponses(Vector responses, Vector acceptances) {
            int agentsLeft = responses.size() - globalResponses;
            globalResponses = 0;

            log.info(getAID().getName() + " got " + agentsLeft + " responses.");

            Bid oldBid = bidsForRounds.get(ROUND_ID);
            BigDecimal bestPriceOffer = oldBid.getPrice();

            ACLMessage reply = new ACLMessage(ACLMessage.CFP);
            Vector<ACLMessage> cfps = new Vector<>();
            Enumeration<?> receivedResponses = responses.elements();

            ArrayList<ACLMessage> replies = new ArrayList<>();

            while (receivedResponses.hasMoreElements()) {
                ACLMessage msg = (ACLMessage) receivedResponses.nextElement();
                if (msg.getPerformative() == ACLMessage.PROPOSE) {
                    BigDecimal proposal = new BigDecimal(msg.getContent());
                    reply = msg.createReply();
                    reply.setPerformative(ACLMessage.CFP);
                    replies.add(reply);
                    if (proposal.compareTo(bestPriceOffer) < 0) {
                        bestPriceOffer = proposal;
                    }
                    cfps.addElement(reply);
                }
            }

            Bid newBid = new Bid(bestPriceOffer, oldBid.getQuantity());
            if (agentsLeft > 1) {
                bidsForRounds.put(ROUND_ID, newBid);

                for (int replyToBuyerIndex = 0; replyToBuyerIndex < replies.size(); replyToBuyerIndex++) {
                    replies
                            .get(replyToBuyerIndex)
                            .setContent(getLocalName() + "|" + bestPriceOffer + "|" + oldBid.getQuantity());
                    cfps.set(replyToBuyerIndex, replies.get(replyToBuyerIndex));
                }

                log.info(agentsLeft + " buyers are still bidding in the current round. Moving on to the next iteration.");
                log.info(getAID().getName()
                        + " is issuing CFP's of "
                        + bidsForRounds.get(ROUND_ID) + ".\n");
                newIteration(cfps);
            } else if (agentsLeft == 1) {
                reply.setPerformative(ACLMessage.REJECT_PROPOSAL);

                if (bestPriceOffer.compareTo(bidsForRounds.get(ROUND_ID).getPrice()) >= 0) {
                    bidsForRounds.put(ROUND_ID, newBid);
                    reply.setContent(getLocalName() + "|" + bestPriceOffer + "|" + oldBid.getQuantity());
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                }

                acceptances.addElement(reply);
            } else {
                log.info("No agent accepted the job.");
            }
        }

    }

}
