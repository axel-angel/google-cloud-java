/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gcloud.compute;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.services.compute.model.Address;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * A Google Compute Engine address. With Compute Engine you can create static external IP addresses
 * that are assigned to your project and persists until you explicitly release them. A region
 * address can be assigned to a Compute Engine instance or to a regional forwarding rule. To create
 * a region address use a {@link RegionAddressId} identity. Compute Engine also allows to create
 * global addresses that are used for global forwarding rules. Both global addresses and global
 * forwarding rules can only be used for HTTP load balancing. To create a global address use a
 * {@link GlobalAddressId} identity.
 *
 * @see <a href="https://cloud.google.com/compute/docs/instances-and-network#reservedaddress">
 *     Static external IP addresses</a>
 * @see <a href="https://cloud.google.com/compute/docs/load-balancing/http/">HTTP Load Balancing</a>
 */
public class AddressInfo implements Serializable {

  static final Function<Address, AddressInfo> FROM_PB_FUNCTION =
      new Function<Address, AddressInfo>() {
        @Override
        public AddressInfo apply(Address pb) {
          return AddressInfo.fromPb(pb);
        }
      };
  static final Function<AddressInfo, Address> TO_PB_FUNCTION =
      new Function<AddressInfo, Address>() {
        @Override
        public Address apply(AddressInfo addressInfo) {
          return addressInfo.toPb();
        }
      };

  private static final long serialVersionUID = 7678434703520207500L;
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = ISODateTimeFormat.dateTime();

  private final String address;
  private final Long creationTimestamp;
  private final String description;
  private final String id;
  private final AddressId addressId;
  private final Status status;
  private final Usage usage;

  /**
   * The status of the address.
   */
  public enum Status {

    /**
     * The address is reserved for the project and is available for use.
     */
    RESERVED,

    /**
     * The address is currently being used and thus not available.
     */
    IN_USE
  }

  /**
   * Base class for a Google Compute Engine address's usage information. Implementations of this
   * class represent different possible usages of a Compute Engine address. {@link InstanceUsage}
   * contains information for region addresses assigned to a Google Compute Engine instance.
   * {@link RegionForwardingUsage} contains information for region addresses assigned to one or more
   * region forwarding rule. {@link GlobalForwardingUsage} contains information for global addresses
   * assigned to one or more global forwarding rule.
   */
  public abstract static class Usage implements Serializable {

    private static final long serialVersionUID = -5028609518171408695L;

    Usage() {}

    /**
     * Returns the identities of resources currently using this address.
     */
    public abstract List<ResourceId> users();

    final boolean baseEquals(Usage usage) {
      return Objects.equals(toPb(), usage.toPb());
    }

    Address toPb() {
      return new Address().setUsers(Lists.transform(users(), new Function<ResourceId, String>() {
        @Override
        public String apply(ResourceId resourceId) {
          return resourceId.selfLink();
        }
      }));
    }

    @SuppressWarnings("unchecked")
    static <T extends Usage> T fromPb(Address addressPb) {
      String url = addressPb.getUsers().get(0);
      if (InstanceId.matchesUrl(url)) {
        return (T) InstanceUsage.fromPb(addressPb);
      } else if (RegionForwardingRuleId.matchesUrl(url)) {
        return (T) RegionForwardingUsage.fromPb(addressPb);
      } else {
        return (T) GlobalForwardingUsage.fromPb(addressPb);
      }
    }
  }

  /**
   * Usage information for a Google Compute Engine region address assigned to a virtual machine
   * instance.
   */
  public static final class InstanceUsage extends Usage {

    private static final long serialVersionUID = -5028609518171408695L;

    private final InstanceId instance;

    InstanceUsage(InstanceId instance) {
      this.instance = checkNotNull(instance);
    }

    /**
     * Returns the identity of the instance using the address.
     */
    public InstanceId instance() {
      return instance;
    }

    @Override
    public List<ResourceId> users() {
      return ImmutableList.<ResourceId>of(instance);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("instance", instance).toString();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof InstanceUsage && baseEquals((InstanceUsage) obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(instance);
    }

    @SuppressWarnings("unchecked")
    static InstanceUsage fromPb(Address addressPb) {
      return new InstanceUsage(InstanceId.fromUrl(addressPb.getUsers().get(0)));
    }
  }

  /**
   * Usage information for a Google Compute Engine region address assigned to one or more region
   * forwarding rules.
   */
  public static final class RegionForwardingUsage extends Usage {

    private static final long serialVersionUID = -4255145869626427363L;

    private final List<RegionForwardingRuleId> forwardingRules;

    RegionForwardingUsage(List<RegionForwardingRuleId> forwardingRules) {
      this.forwardingRules = ImmutableList.copyOf(forwardingRules);
    }

    /**
     * Returns a list of identities of region forwarding rules that are currently using the address.
     */
    public List<RegionForwardingRuleId> forwardingRules() {
      return forwardingRules;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ResourceId> users() {
      return (List<ResourceId>) (List) forwardingRules;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("forwardingRules", forwardingRules).toString();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof RegionForwardingUsage && baseEquals((RegionForwardingUsage) obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(forwardingRules);
    }

    @SuppressWarnings("unchecked")
    static RegionForwardingUsage fromPb(Address addressPb) {
      return new RegionForwardingUsage(
          Lists.transform(addressPb.getUsers(), RegionForwardingRuleId.FROM_URL_FUNCTION));
    }
  }

  /**
   * Usage information for a Google Compute Engine global address assigned to one or more global
   * forwarding rules.
   */
  public static final class GlobalForwardingUsage extends Usage {

    private static final long serialVersionUID = -2974154224319117433L;

    private final List<GlobalForwardingRuleId> forwardingRules;

    GlobalForwardingUsage(List<GlobalForwardingRuleId> forwardingRules) {
      this.forwardingRules = ImmutableList.copyOf(forwardingRules);
    }

    /**
     * Returns a list of identities of global forwarding rules that are currently using the address.
     */
    public List<GlobalForwardingRuleId> forwardingRules() {
      return forwardingRules;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ResourceId> users() {
      return (List<ResourceId>) (List) forwardingRules;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("forwardingRules", forwardingRules).toString();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof GlobalForwardingUsage && baseEquals((GlobalForwardingUsage) obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(forwardingRules);
    }

    @SuppressWarnings("unchecked")
    static GlobalForwardingUsage fromPb(Address addressPb) {
      return new GlobalForwardingUsage(
          Lists.transform(addressPb.getUsers(), GlobalForwardingRuleId.FROM_URL_FUNCTION));
    }
  }

  /**
   * A builder for {@code AddressInfo} objects.
   */
  public abstract static class Builder {

    /**
     * Sets the actual IP address.
     */
    public abstract Builder address(String address);

    abstract Builder creationTimestamp(Long creationTimestamp);

    /**
     * Sets an optional textual description of the address.
     */
    public abstract Builder description(String description);

    abstract Builder id(String id);

    abstract Builder addressId(AddressId addressId);

    abstract Builder status(Status status);

    abstract Builder usage(Usage usage);

    /**
     * Creates an {@code AddressInfo} object.
     */
    public abstract AddressInfo build();
  }

  static final class BuilderImpl extends Builder {

    private String address;
    private Long creationTimestamp;
    private String description;
    private String id;
    private AddressId addressId;
    private Status status;
    private Usage usage;

    BuilderImpl() {}

    BuilderImpl(AddressInfo addressInfo) {
      this.address = addressInfo.address;
      this.creationTimestamp = addressInfo.creationTimestamp;
      this.description = addressInfo.description;
      this.id = addressInfo.id;
      this.addressId = addressInfo.addressId;
      this.status = addressInfo.status;
      this.usage = addressInfo.usage;
    }

    BuilderImpl(Address addressPb) {
      if (RegionAddressId.matchesUrl(addressPb.getSelfLink())) {
        addressId = RegionAddressId.fromUrl(addressPb.getSelfLink());
      } else {
        addressId = GlobalAddressId.fromUrl(addressPb.getSelfLink());
      }
      address = addressPb.getAddress();
      if (addressPb.getCreationTimestamp() != null) {
        creationTimestamp = TIMESTAMP_FORMATTER.parseMillis(addressPb.getCreationTimestamp());
      }
      description = addressPb.getDescription();
      if (addressPb.getId() != null) {
        id = addressPb.getId().toString();
      }
      if (addressPb.getStatus() != null) {
        status = Status.valueOf(addressPb.getStatus());
      }
      if (addressPb.getUsers() != null && addressPb.getUsers().size() > 0) {
        usage = Usage.fromPb(addressPb);
      }
    }

    @Override
    public BuilderImpl address(String address) {
      this.address = address;
      return this;
    }

    @Override
    BuilderImpl creationTimestamp(Long creationTimestamp) {
      this.creationTimestamp = creationTimestamp;
      return this;
    }

    @Override
    public BuilderImpl description(String description) {
      this.description = description;
      return this;
    }

    @Override
    BuilderImpl id(String id) {
      this.id = id;
      return this;
    }

    @Override
    BuilderImpl addressId(AddressId addressId) {
      this.addressId = checkNotNull(addressId);
      return this;
    }

    @Override
    BuilderImpl status(Status status) {
      this.status = status;
      return this;
    }

    @Override
    BuilderImpl usage(Usage usage) {
      this.usage = usage;
      return this;
    }

    @Override
    public AddressInfo build() {
      return new AddressInfo(this);
    }
  }

  AddressInfo(BuilderImpl builder) {
    address = builder.address;
    creationTimestamp = builder.creationTimestamp;
    description = builder.description;
    id = builder.id;
    addressId = checkNotNull(builder.addressId);
    status = builder.status;
    usage = builder.usage;
  }

  /**
   * Returns the static external IP address represented by this object.
   */
  public String address() {
    return address;
  }

  /**
   * Returns the creation timestamp in milliseconds since epoch.
   */
  public Long creationTimestamp() {
    return creationTimestamp;
  }

  /**
   * Returns an optional textual description of the address.
   */
  public String description() {
    return description;
  }

  /**
   * Returns the unique identifier for the address; defined by the service.
   */
  public String id() {
    return id;
  }

  /**
   * Returns the address identity. Returns {@link GlobalAddressId} for a global address, returns
   * {@link RegionAddressId} for a region address.
   */
  @SuppressWarnings("unchecked")
  public <T extends AddressId> T addressId() {
    return (T) addressId;
  }

  /**
   * Returns the status of the address.
   */
  public Status status() {
    return status;
  }

  /**
   * Returns the usage information of the address. Returns a {@link InstanceUsage} object for region
   * addresses that are assigned to VM instances. Returns a {@link RegionForwardingUsage} object for
   * region addresses assigned to region forwarding rules. Returns a {@link GlobalForwardingUsage}
   * object for global addresses assigned to global forwarding rules. Returns {@code null} if the
   * address is not in use.
   */
  @SuppressWarnings("unchecked")
  public <T extends Usage> T usage() {
    return (T) usage;
  }

  /**
   * Returns a builder for the {@code AddressInfo} object.
   */
  public Builder toBuilder() {
    return new BuilderImpl(this);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("address", address)
        .add("creationTimestamp", creationTimestamp)
        .add("description", description)
        .add("id", id)
        .add("addressId", addressId)
        .add("status", status)
        .add("usage", usage)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(address, creationTimestamp, description, id, addressId, status, usage);
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null
        && obj.getClass().equals(AddressInfo.class)
        && Objects.equals(toPb(), ((AddressInfo) obj).toPb());
  }

  AddressInfo setProjectId(String projectId) {
    Builder builder = toBuilder();
    AddressId addressIdWithProject;
    if (addressId instanceof RegionAddressId) {
      addressIdWithProject = this.<RegionAddressId>addressId().setProjectId(projectId);
    } else {
      addressIdWithProject = this.<GlobalAddressId>addressId().setProjectId(projectId);
    }
    return builder.addressId(addressIdWithProject).build();
  }

  Address toPb() {
    Address addressPb = usage != null ? usage.toPb() : new Address();
    addressPb.setAddress(address);
    if (creationTimestamp != null) {
      addressPb.setCreationTimestamp(TIMESTAMP_FORMATTER.print(creationTimestamp));
    }
    addressPb.setDescription(description);
    if (id != null) {
      addressPb.setId(new BigInteger(id));
    }
    addressPb.setName(addressId.address());
    if (addressId.type() == AddressId.Type.REGION) {
      addressPb.setRegion(this.<RegionAddressId>addressId().regionId().selfLink());
    }
    if (status != null) {
      addressPb.setStatus(status.name());
    }
    addressPb.setSelfLink(addressId.selfLink());
    return addressPb;
  }

  /**
   * Returns a builder for the {@code AddressInfo} object given it's identity.
   */
  public static BuilderImpl builder(AddressId addressId) {
    return new BuilderImpl().addressId(addressId);
  }

  /**
   * Returns an {@code AddressInfo} object for the provided identity.
   */
  public static AddressInfo of(AddressId addressId) {
    return builder(addressId).build();
  }

  /**
   * Returns an {@code AddressInfo} object for the provided name. The object corresponds to a global
   * address.
   */
  public static AddressInfo of(String name) {
    return of(GlobalAddressId.of(name));
  }

  /**
   * Returns an {@code AddressInfo} object for the provided region identity and name. The object
   * corresponds to a region address.
   */
  public static AddressInfo of(RegionId regionId, String name) {
    return of(RegionAddressId.of(regionId, name));
  }

  /**
   * Returns an {@code AddressInfo} object for the provided region and address names. The object
   * corresponds to a region address.
   */
  public static AddressInfo of(String region, String name) {
    return of(RegionAddressId.of(region, name));
  }

  static AddressInfo fromPb(Address addressPb) {
    return new BuilderImpl(addressPb).build();
  }
}
