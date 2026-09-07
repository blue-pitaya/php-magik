<?php

class Engine
{
    public int $power;
}

class Car
{
    public function boost(Engine $e)
    {
        return $e->power;
    }
}
