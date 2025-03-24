import type { MetaVariableContext } from './state/metaVariables';
import type { RefLike, Snapshot } from './state/snapshots';

/**
 * Application features that can be enabled by the user.
 * The feature selection cannot be changed while building the proof tree.
 */
export interface SupportedFeatures {
  decl: boolean;
  pairs: boolean;
  io: boolean;
  store: boolean;
  exceptions: boolean;
}

/**
 * Settings that affect renderings. They can be changed while building the proof tree.
 */
export interface DisplaySettings {
  showEmptySignature: boolean;
  showEmptyEnvironment: boolean;
  showEmptyStore: boolean;
}

/**
 * Application context holding information about user settings and state of the tree (variables, snapshots).
 */
export interface Context {
  readonly features: Readonly<SupportedFeatures>;
  readonly displaySettings: Readonly<DisplaySettings>;
  readonly requireTypes: boolean;
  readonly metaVariables: MetaVariableContext;
  readonly nextAddressIndex: RefLike<bigint>;
  readonly snapshot: Snapshot;
}
