/** Typed JS surface over the native `MiasThermal` module (device health snapshot). */
import { NativeModules } from 'react-native';

interface NativeThermalModule {
  getHealth(): Promise<string>;
}

const native: NativeThermalModule | undefined = NativeModules.MiasThermal;

export interface DeviceHealth {
  available: boolean;
  socTempCelsius?: number;
  batteryTempCelsius?: number;
  batteryLevel?: number;
}

export const thermal = {
  isAvailable: native != null,

  async getHealth(): Promise<DeviceHealth> {
    if (!native) return { available: false };
    try {
      return JSON.parse(await native.getHealth()) as DeviceHealth;
    } catch {
      return { available: false };
    }
  },
};
