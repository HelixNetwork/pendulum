package net.helix.pendulum.service.milestone;

////TODO <<<<<<< refactoring-singletons
import net.helix.pendulum.TransactionValidator;
////=======
import net.helix.pendulum.Pendulum;
////TODO >>>>>>> refactoring
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.snapshot.SnapshotProvider;

/**
 * This interface defines the contract for a manager that tries to solidify unsolid milestones by incorporating a
 * background worker that periodically checks the solidity of the milestones and issues transaction requests for the
 * missing transactions until the milestones become solid.<br />
 */
public interface MilestoneSolidifier extends Pendulum.Initializable{
    /**
     * This method allows us to add new milestones to the solidifier that will consequently be solidified.<br />
     *
     * @param milestoneHash Hash of the milestone that shall be solidified
     * @param milestoneIndex index of the milestone that shall be solidified
     */
    void add(Hash milestoneHash, int milestoneIndex);

    /**
     * This method starts the background worker that asynchronously solidifies the milestones.<br />
     */
    void start();

    /**
     * This method shuts down the background worker that asynchronously solidifies the milestones.<br />
     */
    void shutdown();
    
    MilestoneSolidifier init(SnapshotProvider snapshotProvider, TransactionValidator transactionValidator);

}
