package net.helix.hlx.utils.bundletypes;

public abstract class BundleFactory {



    public enum Type {
        milestone, // vote
        registration, // application / sign up / sign off / key transition
        nominee, // ?
        curator // to publish set of nominees. will be removed from public package
    }

    public static BundleTypes create(Type type, final String addr, Boolean sign, int keyIdx, int currentRoundIdx, int maxRoundIdx, long n ) {
        switch (type) {
            case milestone:
                return new MilestoneBundle(addr, sign, keyIdx, currentRoundIdx, maxRoundIdx, n);
            case registration:
                return new RegistrationBundle();
            case nominee:
                return new NomineeBundle();
            case curator:
                return new CuratorBundle();
            default:
                return null;
        }
    }
}