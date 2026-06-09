package com.company.hrms.listener;



import com.company.hrms.dto.SettleOvertimeEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SettleOvertimeEventListener {

    private static final Logger LOGGER = Logger.getLogger(SettleOvertimeEventListener.class.getName());

    /**
     * Ticket LF-204 Solution: Bound strictly to AFTER_COMMIT.
     * This ensures if Supabase throws an error midway, the transaction rolls back 
     * and NO false SMS is ever sent out to the construction worker.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOvertimeSettlementNotification(SettleOvertimeEvent event) {
        try {
            LOGGER.log(Level.INFO, "Database transaction committed successfully. Dispatching SMS notification pipeline...");
            
            // Simulating the external SMS Gateway call
            String messageBody = String.format(
                "Dear Worker, your overtime for %s of ₹%s has been successfully settled.",
                event.getMonth(),
                event.getTotalAmount().toString()
            );
            
            sendSmsViaGateway(event.getWorkerPhone(), messageBody);
            
        } catch (Exception e) {
            // Rule requirement: If notification networks drop, log it and prevent it from crashing the API response!
            LOGGER.log(Level.SEVERE, "SMS Gateway failed to dispatch message, but database state remains safely settled. Error: " + e.getMessage());
        }
    }

    private void sendSmsViaGateway(String phone, String message) {
        // Mocking successful SMS carrier handoff
        System.out.println("------------------------------------");
        System.out.println("SMS SENT TO: " + phone);
        System.out.println("TEXT: " + message);
        System.out.println("------------------------------------");
    }
}
