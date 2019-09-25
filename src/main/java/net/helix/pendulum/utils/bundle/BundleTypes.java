package net.helix.pendulum.utils.bundle;

public enum BundleTypes {
    milestone, // vote
    registration, // application / sign up / sign off / key transition
    validator // to publish set of validators. Only used by validatomanager, thus will be removed from public package
}
