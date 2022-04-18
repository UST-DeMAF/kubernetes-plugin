package ust.tad.kubernetesplugin.kubernetesmodel.service;

import java.util.Objects;

public class Selector {
    
    private String key;

    private String value;


    public Selector() {
    }

    public Selector(String key, String value) {
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

    public Selector key(String key) {
        setKey(key);
        return this;
    }

    public Selector value(String value) {
        setValue(value);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Selector)) {
            return false;
        }
        Selector selector = (Selector) o;
        return Objects.equals(key, selector.key) && Objects.equals(value, selector.value);
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
