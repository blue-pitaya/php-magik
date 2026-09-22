<?php

class Box
{
    public int $value;

    public function __construct(int $value)
    {
        $this->value = $value;
    }

    public function get()
    {
        return $this->value;
    }
}
