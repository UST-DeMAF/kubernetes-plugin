package ust.tad.kubernetesplugin.kubernetesmodel.deployment;

import java.util.Objects;

public class Label {
    
    private String key;

    private String value;


    public Label() {
    }

    public Label(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Label key(String key) {
        setKey(key);
        return this;
    }

    public Label value(String value) {
        setValue(value);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Label)) {
            return false;
        }
        Label label = (Label) o;
        return Objects.equals(key, label.key) && Objects.equals(value, label.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "{" +
            " key='" + getKey() + "'" +
            ", value='" + getValue() + "'" +
            "}";
    }


}
