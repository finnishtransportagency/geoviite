import Select from "react-select";
import React from "react";
import { useTranslation } from "react-i18next";
import { useAppDispatch, useAppSelector } from "../store/store";
import { apiConfigSet } from "../store/config-slice";
import { Environment } from "../api/types";

interface EnvironmentOption {
  value: Environment;
  label: string;
}

function toOption(e: Environment): EnvironmentOption {
  return { value: e, label: e };
}

// Environment choice and API key inputs. Edits are committed on blur or Enter;
// committing resets all loaded data since it may come from a different source.
export const ApiSettings: React.FC = () => {
  const dispatch = useAppDispatch();
  const config = useAppSelector((state) => state.config);
  const [environment, setEnvironment] = React.useState(config.environment);
  const [devApiKey, setDevApiKey] = React.useState(config.devApiKey);
  const [prodApiKey, setProdApiKey] = React.useState(config.prodApiKey);

  const commit = () => {
    if (
      environment !== config.environment ||
      devApiKey !== config.devApiKey ||
      prodApiKey !== config.prodApiKey
    ) {
      dispatch(apiConfigSet({ environment, devApiKey, prodApiKey }));
    }
  };
  const commitOnEnter = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      commit();
    }
  };

  const { t } = useTranslation();
  const environments: Environment[] = ["local", "prod", "test", "dev"];

  return (
    <div className="api-settings">
      <label>
        {t("environment")}{" "}
        <Select<EnvironmentOption>
          options={environments.map(toOption)}
          onChange={(v) => v && setEnvironment(v.value)}
          value={toOption(environment)}
          onBlur={commit}
        />
      </label>
      <label>
        {t("devApiKey")}{" "}
        <input
          type="password"
          size={30}
          value={devApiKey}
          onChange={(e) => setDevApiKey(e.target.value)}
          onBlur={commit}
          onKeyDown={commitOnEnter}
        />
      </label>
      <label>
        {t("prodApiKey")}{" "}
        <input
          type="password"
          size={30}
          value={prodApiKey}
          onChange={(e) => setProdApiKey(e.target.value)}
          onBlur={commit}
          onKeyDown={commitOnEnter}
        />
      </label>
    </div>
  );
};
